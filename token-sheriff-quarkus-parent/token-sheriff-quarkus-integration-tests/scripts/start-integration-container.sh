#!/bin/bash
# Start JWT Integration Tests using Docker Compose

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
ROOT_DIR="$(dirname "$(dirname "$PROJECT_DIR")")"

# shellcheck source=lib-docker-compose.sh
source "${SCRIPT_DIR}/lib-docker-compose.sh"

echo "Starting JWT Integration Tests with Docker Compose"
echo "Project directory: ${PROJECT_DIR}"
echo "Root directory: ${ROOT_DIR}"

# Resolve the compose command (docker compose plugin vs standalone docker-compose)
COMPOSE_BASE="$(resolve_compose_cmd || true)"
if [[ -z "$COMPOSE_BASE" ]]; then
    echo "Error: Docker Compose not available (neither 'docker compose' nor 'docker-compose')"
    exit 1
fi
if ! docker_daemon_up; then
    echo "Error: Docker daemon not running — start Docker/Rancher Desktop first"
    exit 1
fi

cd "${PROJECT_DIR}"

# Check build approach - Native executable + Docker copy vs Docker build
RUNNER_FILE=$(find target/ -name "*-runner" -type f 2>/dev/null | head -n 1)
# Detect image type - prefer JFR if available, fallback to distroless
JFR_IMAGE=$(docker images --format "{{.Repository}}:{{.Tag}}" | grep "^token-sheriff-integration-tests:jfr$" || true)
DISTROLESS_IMAGE=$(docker images --format "{{.Repository}}:{{.Tag}}" | grep "^token-sheriff-integration-tests:distroless$" || true)

if [[ -n "$JFR_IMAGE" ]]; then
    AVAILABLE_IMAGE="$JFR_IMAGE"
    IMAGE_TYPE="jfr"
    export DOCKER_IMAGE_TAG="jfr"
    export DOCKERFILE="Dockerfile.native.jfr"
elif [[ -n "$DISTROLESS_IMAGE" ]]; then
    AVAILABLE_IMAGE="$DISTROLESS_IMAGE"
    IMAGE_TYPE="distroless"
    export DOCKER_IMAGE_TAG="distroless"
    export DOCKERFILE="Dockerfile.native.distroless"
else
    AVAILABLE_IMAGE=""
    IMAGE_TYPE="none"
fi

IMAGE_EXISTS=$([ ! -z "$AVAILABLE_IMAGE" ] && echo "true" || echo "false")

if [[ -n "$RUNNER_FILE" ]] && [[ "$IMAGE_EXISTS" == "true" ]]; then
    echo "Using Maven-built native executable: $(basename "$RUNNER_FILE")"
    echo "Docker image: $AVAILABLE_IMAGE ($IMAGE_TYPE mode)"
    COMPOSE_FILE="docker-compose.yml"
    MODE="native (Maven-built + Docker copy) - $IMAGE_TYPE"
elif [[ "$IMAGE_EXISTS" == "true" ]]; then
    echo "Using Docker-built native image: $AVAILABLE_IMAGE ($IMAGE_TYPE mode)"
    COMPOSE_FILE="docker-compose.yml"
    MODE="native (Docker-built) - $IMAGE_TYPE"
else
    echo "Error: Neither native executable nor Docker image found"
    echo "Expected: target/*-runner file and token-sheriff-integration-tests image"
    echo "Available images:"
    docker images | grep token-sheriff || echo "  No token-sheriff images found"
    echo "Run: mvnw verify -Pintegration-tests -pl token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests"
    exit 1
fi


# Set LOG_TARGET_DIR to project's target directory for Quarkus file logging
export LOG_TARGET_DIR="${PROJECT_DIR}/target"

# Start with Docker Compose (includes Keycloak)
# Support multi-IDP profiles via COMPOSE_PROFILES environment variable
PROFILE_INFO=""
if [[ -n "$COMPOSE_PROFILES" ]]; then
    export COMPOSE_PROFILES
    PROFILE_INFO=" [profiles: $COMPOSE_PROFILES]"
    # Ensure Quarkus profile matches — enables %multi-idp. properties for Dex/Zitadel issuers
    if [[ "$COMPOSE_PROFILES" == *"multi-idp"* ]] && [[ -z "$QUARKUS_PROFILE" || "$QUARKUS_PROFILE" == "prod" ]]; then
        export QUARKUS_PROFILE="multi-idp"
        echo "Set QUARKUS_PROFILE=multi-idp (required for Dex/Zitadel issuer configuration)"
    fi
fi

echo "Starting Docker containers (Quarkus $MODE + Keycloak${PROFILE_INFO})..."
echo "Quarkus logs will be written to: ${LOG_TARGET_DIR}/quarkus.log"
if [[ -n "$COMPOSE_OVERRIDE" ]]; then
    mkdir -p "${PROJECT_DIR}/target/jfr-output"
    echo "Using compose overlay: $COMPOSE_OVERRIDE"
    (cd "${PROJECT_DIR}" && $COMPOSE_BASE -f "$COMPOSE_FILE" -f "$COMPOSE_OVERRIDE" up -d)
else
    (cd "${PROJECT_DIR}" && $COMPOSE_BASE -f "$COMPOSE_FILE" up -d)
fi

# Wait for Keycloak to be ready first
echo "Waiting for Keycloak to be ready..."
for i in {1..60}; do
    if curl -k -s https://localhost:1090/health/ready > /dev/null 2>&1; then
        echo "Keycloak is ready!"
        break
    fi
    if [ $i -eq 60 ]; then
        echo "Error: Keycloak failed to start within 60 seconds"
        echo "Check logs with: docker compose logs keycloak"
        exit 1
    fi
    echo "Waiting for Keycloak... (attempt $i/60)"
    sleep 1
done

# Wait for Dex to be ready (only when multi-idp profile is active)
if [[ "$COMPOSE_PROFILES" == *"multi-idp"* ]]; then
    echo "Waiting for Dex to be ready..."
    for i in {1..30}; do
        if curl -k -s https://localhost:2556/dex/.well-known/openid-configuration > /dev/null 2>&1; then
            echo "Dex is ready!"
            break
        fi
        if [ $i -eq 30 ]; then
            echo "Error: Dex failed to start within 30 seconds"
            echo "Check logs with: docker compose logs dex"
            exit 1
        fi
        echo "Waiting for Dex... (attempt $i/30)"
        sleep 1
    done
fi

# Wait for Zitadel to be ready and run setup (only when multi-idp profile is active)
if [[ "$COMPOSE_PROFILES" == *"multi-idp"* ]]; then
    echo "Waiting for Zitadel to be ready..."
    for i in {1..60}; do
        if curl -s -H "Host: zitadel:8080" http://localhost:3080/debug/ready > /dev/null 2>&1; then
            echo "Zitadel is ready!"
            break
        fi
        if [ $i -eq 60 ]; then
            echo "Error: Zitadel failed to start within 60 seconds"
            echo "Check logs with: docker compose logs zitadel"
            exit 1
        fi
        echo "Waiting for Zitadel... (attempt $i/60)"
        sleep 1
    done

    # Run Zitadel setup script to configure users, roles, and OIDC app
    echo "Running Zitadel setup script..."
    # Copy PAT from Docker container to host-accessible location
    # Retry because Zitadel may still be writing the PAT file after health check passes
    PAT_CONTAINER=$($COMPOSE_BASE ps -q zitadel)
    mkdir -p /tmp/zitadel-admin-pat
    for i in {1..10}; do
        if docker cp "${PAT_CONTAINER}:/machinekey/zitadel-admin-sa.pat" /tmp/zitadel-admin-pat/ 2>/dev/null; then
            if [ -s /tmp/zitadel-admin-pat/zitadel-admin-sa.pat ]; then
                echo "Admin PAT copied from container"
                break
            fi
        fi
        if [ $i -eq 10 ]; then
            echo "Error: Failed to copy Zitadel admin PAT after 10 attempts"
            exit 1
        fi
        echo "Waiting for Zitadel PAT file... (attempt $i/10)"
        sleep 2
    done

    PAT_FILE=/tmp/zitadel-admin-pat/zitadel-admin-sa.pat \
    OUTPUT_DIR="${PROJECT_DIR}" \
    bash "${PROJECT_DIR}/src/main/docker/zitadel/setup.sh"
    if [ $? -eq 0 ]; then
        echo "Zitadel setup complete!"
    else
        echo "Error: Zitadel setup failed"
        echo "Check Zitadel logs with: docker compose logs zitadel"
        exit 1
    fi

    # Trigger a token issuance to force Zitadel to publish signing keys to JWKS.
    # This must happen BEFORE the Quarkus app loads JWKS, otherwise the app gets
    # an empty key set and won't validate Zitadel tokens until the next refresh.
    SVC_ID=$(grep 'service.client-id' "${PROJECT_DIR}/target/zitadel-credentials.properties" | cut -d= -f2)
    SVC_SECRET=$(grep 'service.client-secret' "${PROJECT_DIR}/target/zitadel-credentials.properties" | cut -d= -f2)
    PROJECT_ID=$(grep 'project.id' "${PROJECT_DIR}/target/zitadel-credentials.properties" | cut -d= -f2)
    curl -s -H "Host: zitadel:8080" -u "${SVC_ID}:${SVC_SECRET}" -X POST http://localhost:3080/oauth/v2/token \
        -d "grant_type=client_credentials&scope=openid" > /dev/null 2>&1
    # Wait for JWKS to contain the key
    for i in {1..15}; do
        KEY_COUNT=$(curl -s -H "Host: zitadel:8080" http://localhost:3080/oauth/v2/keys 2>/dev/null \
            | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('keys',[])))" 2>/dev/null)
        if [ "$KEY_COUNT" -gt 0 ] 2>/dev/null; then
            echo "  Zitadel JWKS: ${KEY_COUNT} key(s) published"
            break
        fi
        sleep 1
    done
fi

# Wait for Quarkus service to be ready and measure startup time
echo "Waiting for Quarkus service to be ready..."
START_TIME=$(date +%s)
for i in {1..30}; do
    if curl -k -s https://localhost:10443/q/health/live > /dev/null 2>&1; then
        END_TIME=$(date +%s)
        TOTAL_TIME=$((END_TIME - START_TIME))
        echo "Quarkus service is ready!"
        echo "Actual startup time: ${TOTAL_TIME}s (container + application)"
        break
    fi
    if [ $i -eq 30 ]; then
        echo "Error: Quarkus service failed to start within 30 seconds"
        echo "Check logs with: docker compose logs token-sheriff-integration-tests"
        exit 1
    fi
    echo "Waiting for Quarkus... (attempt $i/30)"
    sleep 1
done

# Wait for Quarkus app to pick up Zitadel JWKS keys via background refresh
if [[ "$COMPOSE_PROFILES" == *"multi-idp"* ]]; then
    echo "Verifying Zitadel token validation works end-to-end..."
    TOKEN_RESPONSE=$(curl -s -H "Host: zitadel:8080" -u "${SVC_ID}:${SVC_SECRET}" -X POST http://localhost:3080/oauth/v2/token \
        -d "grant_type=client_credentials&scope=openid+urn:zitadel:iam:org:project:id:${PROJECT_ID}:aud" 2>/dev/null)
    TOKEN=$(echo "$TOKEN_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('access_token',''))" 2>/dev/null)
    if [ -n "$TOKEN" ]; then
        for i in {1..30}; do
            STATUS=$(curl -k -s -o /dev/null -w "%{http_code}" -X POST https://localhost:10443/jwt/validate \
                -H "Authorization: Bearer ${TOKEN}" 2>/dev/null)
            if [ "$STATUS" = "200" ]; then
                echo "  Zitadel token validates successfully (attempt $i)"
                break
            fi
            if [ $i -eq 30 ]; then
                echo "  Warning: Zitadel token validation not working after 30s — tests may fail"
            fi
            sleep 1
        done
    else
        echo "  Warning: Could not acquire Zitadel token for validation check"
    fi
fi

# Extract native startup time from logs
NATIVE_STARTUP=$($COMPOSE_BASE logs token-sheriff-integration-tests 2>/dev/null | grep "started in" | sed -n 's/.*started in \([0-9.]*\)s.*/\1/p' | tail -1)
if [ ! -z "$NATIVE_STARTUP" ]; then
    echo "Native app startup: ${NATIVE_STARTUP}s (application only)"
fi

# Show actual image size
IMAGE_SIZE=$(docker images --format "table {{.Repository}}:{{.Tag}}\t{{.Size}}" | grep token-sheriff-integration-tests | awk '{print $2}' | head -1)
if [ ! -z "$IMAGE_SIZE" ]; then
    echo "Image size: ${IMAGE_SIZE} (native image)"
fi

echo ""
echo "JWT Integration Benchmark Environment is running!"
echo ""
echo "Application URLs:"
echo "  Health Check:   https://localhost:10443/q/health"
echo "  Metrics:        https://localhost:10443/q/metrics"
echo "  Keycloak:       https://localhost:1443/auth"
if [[ "$COMPOSE_PROFILES" == *"multi-idp"* ]]; then
echo "  Dex:            https://localhost:2556/dex/.well-known/openid-configuration"
echo "  Zitadel:        http://localhost:3080/.well-known/openid-configuration"
fi
echo ""
echo "Quick test commands:"
echo "  curl -k https://localhost:10443/q/health/live"
echo "  curl -k https://localhost:1090/health/ready"
echo ""
echo "To stop: ./scripts/stop-integration-container.sh"
echo "To view logs: docker compose logs -f"
