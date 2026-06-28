#!/bin/bash
# Health live check benchmark runner with metadata embedding
# This script runs the health/live endpoint benchmark and embeds metadata for Prometheus integration

set -e

# Verify required environment variables are set by Maven
: "${WRK_THREADS:?ERROR: WRK_THREADS environment variable is not set}"
: "${WRK_CONNECTIONS:?ERROR: WRK_CONNECTIONS environment variable is not set}"
: "${WRK_DURATION:?ERROR: WRK_DURATION environment variable is not set}"
: "${WRK_TIMEOUT:?ERROR: WRK_TIMEOUT environment variable is not set}"
BENCHMARK_NAME="healthLiveCheck"

# Service URL uses Docker service name (configured in docker-compose.yml)
SERVICE_URL="https://token-sheriff-integration-tests:8443"

# Get compose file directory from environment variable (passed by Maven)
: "${COMPOSE_DIR:?ERROR: COMPOSE_DIR environment variable is not set}"

# Record benchmark start time
BENCHMARK_START_TIME=$(date +%s)
BENCHMARK_START_ISO=$(date -Iseconds)

# Output metadata header (this goes to stdout which Maven captures)
echo "=== BENCHMARK METADATA ==="
echo "benchmark_name: $BENCHMARK_NAME"
echo "start_time: $BENCHMARK_START_TIME"
echo "start_time_iso: $BENCHMARK_START_ISO"
echo "=== WRK OUTPUT ==="
echo ""

# Run wrk health check benchmark using docker compose
# Network and service topology managed by docker-compose.yml
cd "$COMPOSE_DIR"
docker compose run --rm wrk \
    -t"$WRK_THREADS" \
    -c"$WRK_CONNECTIONS" \
    -d"$WRK_DURATION" \
    --timeout "$WRK_TIMEOUT" \
    --latency \
    -s /scripts/health_live_check.lua \
    "$SERVICE_URL/q/health/live"

# Record benchmark end time
BENCHMARK_END_TIME=$(date +%s)
BENCHMARK_END_ISO=$(date -Iseconds)

# Output metadata footer
echo ""
echo "=== BENCHMARK COMPLETE ==="
echo "end_time: $BENCHMARK_END_TIME"
echo "end_time_iso: $BENCHMARK_END_ISO"
echo "duration_seconds: $((BENCHMARK_END_TIME - BENCHMARK_START_TIME))"