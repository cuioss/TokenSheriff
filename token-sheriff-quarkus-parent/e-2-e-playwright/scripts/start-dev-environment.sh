#!/usr/bin/env bash
# Start the E2E Dev-UI test environment:
# 1. Start Keycloak via docker compose
# 2. Build the checked-out modules the integration-tests module needs into a Maven repository
#    private to this run
# 3. Start Quarkus dev mode for the integration-tests module against that repository
# 4. Wait for Quarkus to be ready
#
# Dev mode resolves the project's own modules from a Maven repository, not from the source tree.
# Resolving them from the shared ~/.m2 would test whatever was last installed there, not the
# checked-out code. This script therefore builds those modules into a repository under the
# module's target/ directory and points dev mode at it. The shared ~/.m2 is only read, as a
# read-only fallback for third-party artifacts (maven.repo.local.tail), and is never written.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE_DIR="$(dirname "$SCRIPT_DIR")"
INTEGRATION_TESTS_DIR="$MODULE_DIR/../token-sheriff-quarkus-integration-tests"
PROJECT_ROOT="$(cd "$MODULE_DIR/../.." && pwd)"
TARGET_DIR="$MODULE_DIR/target"
PRIVATE_REPO="$TARGET_DIR/m2-repository"
SHARED_REPO="$HOME/.m2/repository"

mkdir -p "$TARGET_DIR" "$PRIVATE_REPO"

REPO_ARGS=("-Dmaven.repo.local=$PRIVATE_REPO")
if [ -d "$SHARED_REPO" ]; then
    REPO_ARGS+=("-Dmaven.repo.local.tail=$SHARED_REPO")
fi

echo "=== Starting E2E Dev-UI Test Environment ==="
echo "Maven repository for this run: $PRIVATE_REPO"
if [ -d "$SHARED_REPO" ]; then
    echo "Read-only fallback repository: $SHARED_REPO (never written)"
fi

# --- Step 1: Start Keycloak ---
echo "[1/4] Starting Keycloak..."
cd "$MODULE_DIR"
docker compose up -d

echo "[1/4] Waiting for Keycloak health..."
KEYCLOAK_HEALTH_URL="https://localhost:1090/health/ready"
MAX_WAIT=90
WAITED=0
while [ $WAITED -lt $MAX_WAIT ]; do
    if curl -sk "$KEYCLOAK_HEALTH_URL" 2>/dev/null | grep -q '"status".*"UP"'; then
        echo "[1/4] Keycloak is ready (${WAITED}s)"
        break
    fi
    sleep 2
    WAITED=$((WAITED + 2))
    if [ $((WAITED % 10)) -eq 0 ]; then
        echo "[1/4] Still waiting for Keycloak... (${WAITED}s/${MAX_WAIT}s)"
    fi
done

if [ $WAITED -ge $MAX_WAIT ]; then
    echo "[1/4] ERROR: Keycloak did not become ready within ${MAX_WAIT}s"
    docker compose logs keycloak
    exit 1
fi

# --- Step 2: Build the checked-out modules into the private repository ---
# -am builds every reactor module the integration-tests module depends on, so dev mode finds the
# checked-out version of each of them in the private repository before it looks at the fallback.
echo "[2/4] Installing the checked-out modules into $PRIVATE_REPO..."
BUILD_LOG="$TARGET_DIR/private-repo-build.log"
if ! "$PROJECT_ROOT/mvnw" -B -f "$PROJECT_ROOT/pom.xml" install \
    -DskipTests -DskipITs \
    -pl token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests -am \
    "${REPO_ARGS[@]}" \
    < /dev/null > "$BUILD_LOG" 2>&1; then
    echo "[2/4] ERROR: installing the modules into $PRIVATE_REPO failed"
    echo "Last 50 lines of $BUILD_LOG:"
    tail -50 "$BUILD_LOG"
    exit 1
fi
echo "[2/4] Modules installed into $PRIVATE_REPO"

# --- Step 3: Start Quarkus Dev Mode ---
echo "[3/4] Starting Quarkus dev mode against $PRIVATE_REPO..."
cd "$INTEGRATION_TESTS_DIR"

# Build a merged truststore: JVM default CAs + localhost self-signed cert.
# The cui-http library uses java.net.http.HttpClient which relies on the JVM truststore,
# not the Quarkus TLS registry. We must provide a truststore that includes both
# public CA certs (for Maven Central) and the localhost cert (for Keycloak).
LOCALHOST_CERT="$INTEGRATION_TESTS_DIR/src/main/docker/certificates/localhost.crt"
MERGED_TRUSTSTORE="$TARGET_DIR/merged-truststore.p12"
if [ -n "${JAVA_HOME:-}" ]; then
    JAVA_CACERTS="$JAVA_HOME/lib/security/cacerts"
else
    JAVA_CACERTS="$(java -XshowSettings:property -version 2>&1 | grep 'java.home' | awk '{print $3}')/lib/security/cacerts"
fi

echo "[3/4] Building merged truststore..."
cp "$JAVA_CACERTS" "$MERGED_TRUSTSTORE"
chmod u+w "$MERGED_TRUSTSTORE"
keytool -importcert -trustcacerts -noprompt \
    -keystore "$MERGED_TRUSTSTORE" \
    -storepass changeit \
    -alias localhost-e2e \
    -file "$LOCALHOST_CERT" 2>/dev/null || echo "[3/4] Warning: keytool import returned non-zero (cert may already exist)"

# Start Quarkus dev mode in the background
# - enforceBuildGoal=false: skip check that build goal ran before dev
# - console.enabled=false: prevent interactive console from reading stdin in CI
# - log.console.color=false: clean log output without ANSI codes
# - javax.net.ssl.trustStore: merged truststore with CAs + localhost cert
# - REPO_ARGS: resolve from the private repository built in step 2, the shared ~/.m2 read only
MAVEN_OPTS="-Djavax.net.ssl.trustStore=$MERGED_TRUSTSTORE -Djavax.net.ssl.trustStorePassword=changeit" \
"$PROJECT_ROOT/mvnw" quarkus:dev \
    "${REPO_ARGS[@]}" \
    -Dquarkus.enforceBuildGoal=false \
    -Dquarkus.analytics.disabled=true \
    -Dquarkus.test.continuous-testing=disabled \
    -Dquarkus.console.enabled=false \
    -Dquarkus.log.console.color=false \
    < /dev/null > "$TARGET_DIR/quarkus-dev.log" 2>&1 &

QUARKUS_PID=$!
echo "$QUARKUS_PID" > "$TARGET_DIR/quarkus-dev.pid"
echo "[3/4] Quarkus dev mode started (PID: $QUARKUS_PID)"

# --- Step 4: Wait for Quarkus readiness ---
echo "[4/4] Waiting for Quarkus to start..."
# Use 127.0.0.1 instead of localhost to avoid IPv6 resolution issues in CI
# Check health endpoint first (always available), then verify Dev-UI
HEALTH_URL="http://127.0.0.1:8080/q/health/ready"
MAX_WAIT=120
WAITED=0
while [ $WAITED -lt $MAX_WAIT ]; do
    # Check if the process is still running
    if ! kill -0 "$QUARKUS_PID" 2>/dev/null; then
        echo "[4/4] ERROR: Quarkus dev mode process died"
        echo "Last 50 lines of log:"
        tail -50 "$TARGET_DIR/quarkus-dev.log"
        exit 1
    fi

    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$HEALTH_URL" 2>/dev/null || echo "000")
    if echo "$HTTP_CODE" | grep -qE "^(200|301|302|303)$"; then
        echo "[4/4] Quarkus health endpoint ready (${WAITED}s, HTTP $HTTP_CODE)"
        break
    fi
    sleep 2
    WAITED=$((WAITED + 2))
    if [ $((WAITED % 10)) -eq 0 ]; then
        echo "[4/4] Still waiting for Quarkus... (${WAITED}s/${MAX_WAIT}s, last HTTP=$HTTP_CODE)"
    fi
done

if [ $WAITED -ge $MAX_WAIT ]; then
    echo "[4/4] ERROR: Quarkus did not become ready within ${MAX_WAIT}s"
    echo "[4/4] Last HTTP code: $HTTP_CODE"
    echo "Last 50 lines of log:"
    tail -50 "$TARGET_DIR/quarkus-dev.log"
    exit 1
fi

# Diagnostic: probe multiple endpoints to understand Dev-UI availability
echo "[4/4] Probing Quarkus endpoints..."
for PROBE_URL in \
    "http://127.0.0.1:8080/q/health/ready" \
    "http://127.0.0.1:8080/q/dev-ui/" \
    "https://127.0.0.1:8443/q/dev-ui/" \
    "https://127.0.0.1:8443/q/dev-ui/extensions"; do
    PROBE_CODE=$(curl -sk -o /dev/null -w "%{http_code}" "$PROBE_URL" 2>/dev/null || echo "ERR")
    echo "  $PROBE_CODE $PROBE_URL"
done

# Dump Keycloak container logs for diagnostics
echo "[4/4] Keycloak container logs (last 10 lines):"
cd "$MODULE_DIR"
docker compose logs --tail=10 keycloak 2>/dev/null || echo "  (no Keycloak logs available)"

echo "=== E2E Dev-UI Test Environment Ready ==="
echo "  Keycloak:    https://localhost:1443"
echo "  Quarkus:     https://127.0.0.1:8443"
echo "  Dev-UI:      https://127.0.0.1:8443/q/dev-ui/"
echo "  Quarkus PID: $QUARKUS_PID"
echo "  Maven repo:  $PRIVATE_REPO"
