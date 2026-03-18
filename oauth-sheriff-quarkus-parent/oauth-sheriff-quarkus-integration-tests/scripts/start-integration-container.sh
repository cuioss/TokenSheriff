#!/bin/bash
# Start JWT Integration Tests using Docker Compose

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
ROOT_DIR="$(dirname "$(dirname "$PROJECT_DIR")")"

echo "🚀 Starting JWT Integration Tests with Docker Compose"
echo "Project directory: ${PROJECT_DIR}"
echo "Root directory: ${ROOT_DIR}"

cd "${PROJECT_DIR}"

# Check build approach - Native executable + Docker copy vs Docker build
RUNNER_FILE=$(find target/ -name "*-runner" -type f 2>/dev/null | head -n 1)
# Detect image type - prefer JFR if available, fallback to distroless
JFR_IMAGE=$(docker images --format "{{.Repository}}:{{.Tag}}" | grep "^oauth-sheriff-integration-tests:jfr$" || true)
DISTROLESS_IMAGE=$(docker images --format "{{.Repository}}:{{.Tag}}" | grep "^oauth-sheriff-integration-tests:distroless$" || true)

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
    echo "📦 Using Maven-built native executable: $(basename "$RUNNER_FILE")"
    echo "🐳 Docker image: $AVAILABLE_IMAGE ($IMAGE_TYPE mode)"
    COMPOSE_FILE="docker-compose.yml"
    MODE="native (Maven-built + Docker copy) - $IMAGE_TYPE"
elif [[ "$IMAGE_EXISTS" == "true" ]]; then
    echo "📦 Using Docker-built native image: $AVAILABLE_IMAGE ($IMAGE_TYPE mode)"
    COMPOSE_FILE="docker-compose.yml"
    MODE="native (Docker-built) - $IMAGE_TYPE"
else
    echo "❌ Neither native executable nor Docker image found"
    echo "Expected: target/*-runner file and oauth-sheriff-integration-tests image"
    echo "Available images:"
    docker images | grep oauth-sheriff || echo "  No oauth-sheriff images found"
    echo "Run: mvnw verify -Pintegration-tests -pl oauth-sheriff-quarkus-parent/oauth-sheriff-quarkus-integration-tests"
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
        echo "📋 Set QUARKUS_PROFILE=multi-idp (required for Dex/Zitadel issuer configuration)"
    fi
fi

echo "🐳 Starting Docker containers (Quarkus $MODE + Keycloak${PROFILE_INFO})..."
echo "📁 Quarkus logs will be written to: ${LOG_TARGET_DIR}/quarkus.log"
if [[ -n "$COMPOSE_OVERRIDE" ]]; then
    mkdir -p "${PROJECT_DIR}/target/jfr-output"
    echo "📄 Using compose overlay: $COMPOSE_OVERRIDE"
    (cd "${PROJECT_DIR}" && docker compose -f "$COMPOSE_FILE" -f "$COMPOSE_OVERRIDE" up -d)
else
    (cd "${PROJECT_DIR}" && docker compose -f "$COMPOSE_FILE" up -d)
fi

# Wait for Keycloak to be ready first
echo "⏳ Waiting for Keycloak to be ready..."
for i in {1..60}; do
    if curl -k -s https://localhost:1090/health/ready > /dev/null 2>&1; then
        echo "✅ Keycloak is ready!"
        break
    fi
    if [ $i -eq 60 ]; then
        echo "❌ Keycloak failed to start within 60 seconds"
        echo "Check logs with: docker compose logs keycloak"
        exit 1
    fi
    echo "⏳ Waiting for Keycloak... (attempt $i/60)"
    sleep 1
done

# Wait for Dex to be ready (only when multi-idp profile is active)
if [[ "$COMPOSE_PROFILES" == *"multi-idp"* ]]; then
    echo "⏳ Waiting for Dex to be ready..."
    for i in {1..30}; do
        if curl -k -s https://localhost:2556/dex/.well-known/openid-configuration > /dev/null 2>&1; then
            echo "✅ Dex is ready!"
            break
        fi
        if [ $i -eq 30 ]; then
            echo "❌ Dex failed to start within 30 seconds"
            echo "Check logs with: docker compose logs dex"
            exit 1
        fi
        echo "⏳ Waiting for Dex... (attempt $i/30)"
        sleep 1
    done
fi

# Wait for Zitadel to be ready and run setup (only when multi-idp profile is active)
if [[ "$COMPOSE_PROFILES" == *"multi-idp"* ]]; then
    echo "⏳ Waiting for Zitadel to be ready..."
    for i in {1..60}; do
        if curl -s -H "Host: zitadel:3080" http://localhost:3080/debug/ready > /dev/null 2>&1; then
            echo "✅ Zitadel is ready!"
            break
        fi
        if [ $i -eq 60 ]; then
            echo "❌ Zitadel failed to start within 60 seconds"
            echo "Check logs with: docker compose logs zitadel"
            exit 1
        fi
        echo "⏳ Waiting for Zitadel... (attempt $i/60)"
        sleep 1
    done

    # Run Zitadel setup script to configure users, roles, and OIDC app
    echo "🔧 Running Zitadel setup script..."
    # Copy PAT from Docker container to host-accessible location
    # Retry because Zitadel may still be writing the PAT file after health check passes
    PAT_CONTAINER=$(docker compose ps -q zitadel)
    mkdir -p /tmp/zitadel-admin-pat
    for i in {1..10}; do
        if docker cp "${PAT_CONTAINER}:/machinekey/zitadel-admin-sa.pat" /tmp/zitadel-admin-pat/ 2>/dev/null; then
            if [ -s /tmp/zitadel-admin-pat/zitadel-admin-sa.pat ]; then
                echo "✅ Admin PAT copied from container"
                break
            fi
        fi
        if [ $i -eq 10 ]; then
            echo "❌ Failed to copy Zitadel admin PAT after 10 attempts"
            exit 1
        fi
        echo "⏳ Waiting for Zitadel PAT file... (attempt $i/10)"
        sleep 2
    done

    PAT_FILE=/tmp/zitadel-admin-pat/zitadel-admin-sa.pat \
    OUTPUT_DIR="${PROJECT_DIR}" \
    bash "${PROJECT_DIR}/src/main/docker/zitadel/setup.sh"
    if [ $? -eq 0 ]; then
        echo "✅ Zitadel setup complete!"
    else
        echo "❌ Zitadel setup failed"
        echo "Check Zitadel logs with: docker compose logs zitadel"
        exit 1
    fi
fi

# Wait for Quarkus service to be ready and measure startup time
echo "⏳ Waiting for Quarkus service to be ready..."
START_TIME=$(date +%s)
for i in {1..30}; do
    if curl -k -s https://localhost:10443/q/health/live > /dev/null 2>&1; then
        END_TIME=$(date +%s)
        TOTAL_TIME=$((END_TIME - START_TIME))
        echo "✅ Quarkus service is ready!"
        echo "📈 Actual startup time: ${TOTAL_TIME}s (container + application)"
        break
    fi
    if [ $i -eq 30 ]; then
        echo "❌ Quarkus service failed to start within 30 seconds"
        echo "Check logs with: docker compose logs oauth-sheriff-integration-tests"
        exit 1
    fi
    echo "⏳ Waiting for Quarkus... (attempt $i/30)"
    sleep 1
done

# Wait for Zitadel JWKS keys to be picked up by app (keys may rotate after init)
if [[ "$COMPOSE_PROFILES" == *"multi-idp"* ]]; then
    SVC_ID=$(grep 'service.client-id' "${PROJECT_DIR}/target/zitadel-credentials.properties" | cut -d= -f2)
    SVC_SECRET=$(grep 'service.client-secret' "${PROJECT_DIR}/target/zitadel-credentials.properties" | cut -d= -f2)
    PROJECT_ID=$(grep 'project.id' "${PROJECT_DIR}/target/zitadel-credentials.properties" | cut -d= -f2)
    echo "⏳ Waiting for Zitadel token validation to work..."
    echo "  Zitadel JWKS URL (from inside Docker): http://zitadel:8080/oauth/v2/keys"
    echo "  Zitadel JWKS URL (from host): http://localhost:3080/oauth/v2/keys"
    # Verify JWKS endpoint is reachable from host
    JWKS_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "Host: zitadel:3080" http://localhost:3080/oauth/v2/keys 2>/dev/null)
    echo "  JWKS endpoint status: ${JWKS_STATUS}"
    for i in {1..90}; do
        TOKEN_RESPONSE=$(curl -s -H "Host: zitadel:3080" -u "${SVC_ID}:${SVC_SECRET}" -X POST http://localhost:3080/oauth/v2/token \
            -d "grant_type=client_credentials&scope=profile+email+urn:zitadel:iam:org:project:id:${PROJECT_ID}:aud" 2>/dev/null)
        TOKEN=$(echo "$TOKEN_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('access_token',''))" 2>/dev/null)
        if [ -n "$TOKEN" ]; then
            STATUS=$(curl -k -s -o /dev/null -w "%{http_code}" -X POST https://localhost:10443/jwt/validate \
                -H "Content-Type: application/json" -d "{\"token\":\"${TOKEN}\",\"tokenType\":\"ACCESS_TOKEN\"}" 2>/dev/null)
            if [ "$STATUS" = "200" ]; then
                echo "✅ Zitadel tokens validate successfully! (attempt $i)"
                break
            fi
            if (( i % 10 == 0 )); then
                echo "⏳ Token acquired but validation returned $STATUS (attempt $i/90)"
                # Decode token to show issuer claim
                ISS=$(echo "$TOKEN" | cut -d. -f2 | python3 -c "import sys,base64,json; s=sys.stdin.read().strip(); s+='='*(4-len(s)%4); print(json.loads(base64.urlsafe_b64decode(s)).get('iss','?'))" 2>/dev/null)
                echo "  Token issuer (iss): ${ISS}"
                # Show validation response body for debugging
                BODY=$(curl -k -s -X POST https://localhost:10443/jwt/validate \
                    -H "Content-Type: application/json" -d "{\"token\":\"${TOKEN}\",\"tokenType\":\"ACCESS_TOKEN\"}" 2>/dev/null)
                echo "  Response: ${BODY:0:200}"
                # Also try Bearer header approach
                BEARER_STATUS=$(curl -k -s -o /dev/null -w "%{http_code}" -X POST https://localhost:10443/jwt/validate \
                    -H "Authorization: Bearer ${TOKEN}" 2>/dev/null)
                echo "  Bearer header status: ${BEARER_STATUS}"
            fi
        else
            if (( i % 10 == 0 )); then
                echo "⏳ Token acquisition failed (attempt $i/90)"
                echo "  Response: ${TOKEN_RESPONSE:0:200}"
            fi
        fi
        if [ $i -eq 90 ]; then
            echo "❌ Zitadel token validation not ready after 90s"
            echo "JWKS refresh has not picked up Zitadel keys. Check Quarkus logs and Zitadel JWKS endpoint."
            # Dump Quarkus logs for JWKS-related entries
            docker compose logs oauth-sheriff-integration-tests 2>/dev/null | grep -i "jwks\|zitadel\|issuer" | tail -20
            exit 1
        fi
        sleep 1
    done
fi

# Extract native startup time from logs
NATIVE_STARTUP=$(docker compose logs oauth-sheriff-integration-tests 2>/dev/null | grep "started in" | sed -n 's/.*started in \([0-9.]*\)s.*/\1/p' | tail -1)
if [ ! -z "$NATIVE_STARTUP" ]; then
    echo "⚡ Native app startup: ${NATIVE_STARTUP}s (application only)"
fi

# Show actual image size
IMAGE_SIZE=$(docker images --format "table {{.Repository}}:{{.Tag}}\t{{.Size}}" | grep oauth-sheriff-integration-tests | awk '{print $2}' | head -1)
if [ ! -z "$IMAGE_SIZE" ]; then
    echo "📦 Image size: ${IMAGE_SIZE} (native image)"
fi

echo ""
echo "🎉 JWT Integration Benchmark Environment is running!"
echo ""
echo "📱 Application URLs:"
echo "  🔍 Health Check:   https://localhost:10443/q/health"
echo "  📊 Metrics:        https://localhost:10443/q/metrics"
echo "  🔑 Keycloak:       https://localhost:1443/auth"
if [[ "$COMPOSE_PROFILES" == *"multi-idp"* ]]; then
echo "  🔑 Dex:            https://localhost:2556/dex/.well-known/openid-configuration"
echo "  🔑 Zitadel:        http://localhost:3080/.well-known/openid-configuration"
fi
echo ""
echo "🧪 Quick test commands:"
echo "  curl -k https://localhost:10443/q/health/live"
echo "  curl -k https://localhost:1090/health/ready"
echo ""
echo "🛑 To stop: ./scripts/stop-integration-container.sh"
echo "📋 To view logs: docker compose logs -f"
