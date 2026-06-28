#!/usr/bin/env bash
# Stop the E2E Dev-UI test environment:
# 1. Kill Quarkus dev mode via stored PID
# 2. Stop Keycloak via docker compose
# Non-fatal exit codes - containers may not be running
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE_DIR="$(dirname "$SCRIPT_DIR")"
TARGET_DIR="$MODULE_DIR/target"

echo "=== Stopping E2E Dev-UI Test Environment ==="

# --- Step 1: Stop Quarkus Dev Mode ---
PID_FILE="$TARGET_DIR/quarkus-dev.pid"
if [ -f "$PID_FILE" ]; then
    QUARKUS_PID=$(cat "$PID_FILE")
    echo "[1/2] Stopping Quarkus dev mode (PID: $QUARKUS_PID)..."
    if kill -0 "$QUARKUS_PID" 2>/dev/null; then
        # Kill child processes first (the actual Java/Quarkus process), then the wrapper
        pkill -TERM -P "$QUARKUS_PID" 2>/dev/null || kill "$QUARKUS_PID" 2>/dev/null || true
        # Wait for graceful shutdown
        WAITED=0
        while kill -0 "$QUARKUS_PID" 2>/dev/null && [ $WAITED -lt 15 ]; do
            sleep 1
            WAITED=$((WAITED + 1))
        done
        # Force kill if still running
        if kill -0 "$QUARKUS_PID" 2>/dev/null; then
            echo "[1/2] Force killing Quarkus dev mode..."
            kill -9 "$QUARKUS_PID" 2>/dev/null || true
        fi
        echo "[1/2] Quarkus dev mode stopped"
    else
        echo "[1/2] Quarkus dev mode not running (PID: $QUARKUS_PID)"
    fi
    rm -f "$PID_FILE"
else
    echo "[1/2] No PID file found, skipping Quarkus shutdown"
fi

# --- Step 2: Stop Keycloak ---
echo "[2/2] Stopping Keycloak..."
cd "$MODULE_DIR"
docker compose down 2>/dev/null || true

echo "=== E2E Dev-UI Test Environment Stopped ==="
exit 0
