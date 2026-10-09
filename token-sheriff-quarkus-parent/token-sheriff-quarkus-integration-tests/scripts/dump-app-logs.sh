#!/bin/bash

# Application Container Log Dumping Script
# Dumps the log of the application under test and, when the retry probe was started, the probe's
# container log and its log file as well.
# Usage: ./dump-app-logs.sh <target-directory>
# Example: ./dump-app-logs.sh target

set -euo pipefail

# Configuration
APP_SERVICE_NAME="token-sheriff-integration-tests"
PROBE_SERVICE_NAME="token-sheriff-retry-probe"
PROBE_COMPOSE_PROFILE="retry-probe"
TIMESTAMP=$(date +"%Y-%m-%d_%H-%M-%S")
APP_LOG_FILENAME="app-logs-${TIMESTAMP}.txt"
# Starts with "app-logs-", so the CI upload glob target/app-logs-*.txt picks it up
PROBE_LOG_FILENAME="app-logs-retry-probe-${TIMESTAMP}.txt"

# Parameter validation
if [ $# -ne 1 ]; then
    echo "Error: Target directory parameter required"
    echo "Usage: $0 <target-directory>"
    exit 1
fi

TARGET_DIR="$1"

# Create target directory if it doesn't exist
if [ ! -d "$TARGET_DIR" ]; then
    mkdir -p "$TARGET_DIR"
fi

# Resolve absolute path
TARGET_ABS_PATH=$(cd "$TARGET_DIR" && pwd)
APP_LOG_FILE_PATH="${TARGET_ABS_PATH}/${APP_LOG_FILENAME}"
PROBE_LOG_FILE_PATH="${TARGET_ABS_PATH}/${PROBE_LOG_FILENAME}"
# Log file the probe writes itself (bind mount, see docker-compose.yml)
PROBE_QUARKUS_LOG="${RETRY_PROBE_LOG_DIR:-${TARGET_ABS_PATH}/retry-probe}/quarkus.log"

echo "Dumping application container logs..."
echo "Output file: $APP_LOG_FILE_PATH"

# Use docker compose to resolve the service name (works regardless of container naming)
if docker compose logs "$APP_SERVICE_NAME" > "$APP_LOG_FILE_PATH" 2>&1; then
    LOG_SIZE=$(wc -l < "$APP_LOG_FILE_PATH")
    FILE_SIZE=$(du -h "$APP_LOG_FILE_PATH" | cut -f1)
    echo "Successfully dumped $LOG_SIZE lines ($FILE_SIZE)"
    echo "Full path: $APP_LOG_FILE_PATH"

    # Echo diagnostic lines to stdout for CI visibility
    echo ""
    echo "=== WARNING/ERROR lines from app container ==="
    grep -i "WARN\|ERROR\|WARNING\|SEVERE" "$APP_LOG_FILE_PATH" | grep -v "Node.js\|deprecated" || echo "  (none)"
    echo ""
    echo "=== Token-Sheriff lines from app container ==="
    grep -i "TokenSheriff\|issuer\|JWKS\|Bearer\|token.*valid" "$APP_LOG_FILE_PATH" | head -30 || echo "  (none)"
    echo ""
else
    echo "Warning: Could not dump app logs (container may not be running)"
fi

# Retry probe (compose profile retry-probe). The profile is named explicitly, because this script
# runs without COMPOSE_PROFILES and the service is invisible to compose otherwise. A run that never
# started the probe has no such container, and nothing below is printed or written.
PROBE_CONTAINER=$(docker compose --profile "$PROBE_COMPOSE_PROFILE" ps -a -q "$PROBE_SERVICE_NAME" 2>/dev/null || true)
if [ -z "$PROBE_CONTAINER" ]; then
    exit 0
fi

echo "Dumping retry probe container logs..."
echo "Output file: $PROBE_LOG_FILE_PATH"

if docker compose --profile "$PROBE_COMPOSE_PROFILE" logs "$PROBE_SERVICE_NAME" > "$PROBE_LOG_FILE_PATH" 2>&1; then
    echo "Successfully dumped $(wc -l < "$PROBE_LOG_FILE_PATH") lines"
    echo ""
    echo "=== WARNING/ERROR lines from retry probe container ==="
    grep -i "WARN\|ERROR\|WARNING\|SEVERE" "$PROBE_LOG_FILE_PATH" || echo "  (none)"
    echo ""
    echo "=== Retry lines (HTTP-111 / HTTP-112) from retry probe container ==="
    grep "HTTP-111\|HTTP-112" "$PROBE_LOG_FILE_PATH" || echo "  (none)"
    echo ""
else
    echo "Warning: Could not dump retry probe logs"
fi

echo "=== Retry probe state ==="
docker inspect --format 'status={{.State.Status}} exitCode={{.State.ExitCode}} restarts={{.RestartCount}}' "$PROBE_CONTAINER" \
    || echo "  (container state not available)"
echo ""

# The file WellKnownRetryConfigSpecIT counts its lines in
echo "=== Retry probe log file: $PROBE_QUARKUS_LOG ==="
if [ -f "$PROBE_QUARKUS_LOG" ]; then
    cat "$PROBE_QUARKUS_LOG"
    # Appended to the uploaded dump, so the artifact holds both views of the probe
    {
        echo ""
        echo "=== Retry probe log file: $PROBE_QUARKUS_LOG ==="
        cat "$PROBE_QUARKUS_LOG"
    } >> "$PROBE_LOG_FILE_PATH"
else
    echo "  (file does not exist)"
fi
echo ""
