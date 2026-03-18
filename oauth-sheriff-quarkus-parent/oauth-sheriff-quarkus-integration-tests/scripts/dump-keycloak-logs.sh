#!/bin/bash

# Keycloak Container Log Dumping Script
# Usage: ./dump-keycloak-logs.sh <target-directory>
# Example: ./dump-keycloak-logs.sh target
# Example: ./dump-keycloak-logs.sh ../../benchmarking/benchmark-integration-wrk/target
#
# Note: Quarkus logs are now written directly to target/quarkus.log via file logging

set -euo pipefail

# Configuration
KEYCLOAK_SERVICE_NAME="keycloak"
TIMESTAMP=$(date +"%Y-%m-%d_%H-%M-%S")
KEYCLOAK_LOG_FILENAME="keycloak-logs-${TIMESTAMP}.txt"

# Parameter validation
if [ $# -ne 1 ]; then
    echo "❌ Error: Target directory parameter required"
    echo "Usage: $0 <target-directory>"
    echo "Example: $0 target"
    exit 1
fi

TARGET_DIR="$1"

# Create target directory if it doesn't exist
if [ ! -d "$TARGET_DIR" ]; then
    echo "📁 Creating target directory: $TARGET_DIR"
    mkdir -p "$TARGET_DIR"
fi

# Resolve absolute path for clarity
TARGET_ABS_PATH=$(cd "$TARGET_DIR" && pwd)
KEYCLOAK_LOG_FILE_PATH="${TARGET_ABS_PATH}/${KEYCLOAK_LOG_FILENAME}"

echo "🚀 Dumping Keycloak container logs..."
echo "📝 Output file: $KEYCLOAK_LOG_FILE_PATH"

# Use docker compose to resolve the service name (works regardless of container naming)
if docker compose logs "$KEYCLOAK_SERVICE_NAME" > "$KEYCLOAK_LOG_FILE_PATH" 2>&1; then
    LOG_SIZE=$(wc -l < "$KEYCLOAK_LOG_FILE_PATH")
    FILE_SIZE=$(du -h "$KEYCLOAK_LOG_FILE_PATH" | cut -f1)
    echo "✅ Successfully dumped $LOG_SIZE lines ($FILE_SIZE)"
    echo "📍 Full path: $KEYCLOAK_LOG_FILE_PATH"
else
    echo "⚠️  Could not dump Keycloak logs (container may not be running)"
    exit 0
fi