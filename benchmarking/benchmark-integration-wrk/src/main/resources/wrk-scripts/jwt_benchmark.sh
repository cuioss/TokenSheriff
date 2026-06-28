#!/bin/bash
# JWT benchmark runner wrapper for Maven execution
# This script runs the JWT benchmark and ensures metadata is captured in Maven's outputFile

set -e

# Verify required environment variables are set by Maven
: "${KEYCLOAK_URL:?ERROR: KEYCLOAK_URL environment variable is not set}"
: "${REALM:?ERROR: REALM environment variable is not set}"
: "${CLIENT_ID:?ERROR: CLIENT_ID environment variable is not set}"
: "${CLIENT_SECRET:?ERROR: CLIENT_SECRET environment variable is not set}"
: "${USERNAME:?ERROR: USERNAME environment variable is not set}"
: "${PASSWORD:?ERROR: PASSWORD environment variable is not set}"
: "${TOKEN_COUNT:?ERROR: TOKEN_COUNT environment variable is not set}"
: "${WRK_THREADS:?ERROR: WRK_THREADS environment variable is not set}"
: "${WRK_CONNECTIONS:?ERROR: WRK_CONNECTIONS environment variable is not set}"
: "${WRK_DURATION:?ERROR: WRK_DURATION environment variable is not set}"
: "${WRK_TIMEOUT:?ERROR: WRK_TIMEOUT environment variable is not set}"
BENCHMARK_NAME="jwtValidation"

# Service URL uses Docker service name (configured in docker-compose.yml)
SERVICE_URL="https://token-sheriff-integration-tests:8443"

# Get script directory for fetch_tokens.sh
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Get compose file directory from environment variable (passed by Maven)
: "${COMPOSE_DIR:?ERROR: COMPOSE_DIR environment variable is not set}"

# Record benchmark start time
BENCHMARK_START_TIME=$(date +%s)
BENCHMARK_START_ISO=$(date -Iseconds)

# Output metadata header to stdout (captured by Maven)
echo "=== BENCHMARK METADATA ==="
echo "benchmark_name: $BENCHMARK_NAME"
echo "start_time: $BENCHMARK_START_TIME"
echo "start_time_iso: $BENCHMARK_START_ISO"
echo "=== WRK OUTPUT ==="
echo ""

# Fetch tokens directly to environment variable (no file I/O)
FETCH_STDERR_LOG=$(mktemp)
echo "Fetching $TOKEN_COUNT tokens from Keycloak (in-memory)..."
TOKEN_DATA=$(bash "$SCRIPT_DIR/fetch_tokens.sh" \
    "$KEYCLOAK_URL" \
    "$REALM" \
    "$CLIENT_ID" \
    "$CLIENT_SECRET" \
    "$USERNAME" \
    "$PASSWORD" \
    "$TOKEN_COUNT" \
    "env" 2> "$FETCH_STDERR_LOG")

if [ -z "$TOKEN_DATA" ]; then
    echo "ERROR: Failed to fetch tokens from Keycloak. Stderr:"
    cat "$FETCH_STDERR_LOG"
    rm -f "$FETCH_STDERR_LOG"
    exit 1
fi
rm -f "$FETCH_STDERR_LOG"

TOKEN_LINE_COUNT=$(echo "$TOKEN_DATA" | wc -l | xargs)
echo "Successfully loaded $TOKEN_LINE_COUNT tokens in memory"
echo ""

# Run wrk with the optimized script using docker compose
# Token data passed as environment variable to the container
# Network and service topology managed by docker-compose.yml
cd "$COMPOSE_DIR"
docker compose run --rm -e TOKEN_DATA="$TOKEN_DATA" wrk \
    -t"$WRK_THREADS" \
    -c"$WRK_CONNECTIONS" \
    -d"$WRK_DURATION" \
    --timeout "$WRK_TIMEOUT" \
    --latency \
    -s /scripts/jwt_benchmark.lua \
    "$SERVICE_URL/jwt/validate"

# Record benchmark end time and output to stdout
BENCHMARK_END_TIME=$(date +%s)
BENCHMARK_END_ISO=$(date -Iseconds)

echo ""
echo "=== BENCHMARK COMPLETE ==="
echo "end_time: $BENCHMARK_END_TIME"
echo "end_time_iso: $BENCHMARK_END_ISO"
echo "duration_seconds: $((BENCHMARK_END_TIME - BENCHMARK_START_TIME))"