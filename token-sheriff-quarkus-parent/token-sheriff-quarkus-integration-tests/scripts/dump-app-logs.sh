#!/bin/bash

# Application Container Log Dumping Script
# Usage: ./dump-app-logs.sh <target-directory>
# Example: ./dump-app-logs.sh target

set -euo pipefail

# Configuration
APP_SERVICE_NAME="token-sheriff-integration-tests"
TIMESTAMP=$(date +"%Y-%m-%d_%H-%M-%S")
APP_LOG_FILENAME="app-logs-${TIMESTAMP}.txt"

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
    exit 0
fi
