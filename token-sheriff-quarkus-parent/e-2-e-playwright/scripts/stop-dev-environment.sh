#!/usr/bin/env bash
# Stop the E2E Dev-UI test environment:
# 1. Stop Quarkus dev mode: the stored PID and every process below it
# 2. Stop Keycloak via docker compose
# Non-fatal exit codes - containers may not be running.
#
# Every step is bounded, so the teardown cannot outlast the CI job:
# - the stored PID is the Maven process started by start-dev-environment.sh; Quarkus dev mode runs
#   the application in a child JVM below it. Signalling only the direct children and force-killing
#   only the stored PID can leave that child JVM running, so the whole process tree is collected
#   first and every member is signalled, waited for and, if needed, force-killed.
# - `docker compose down` is given a stop timeout and, where coreutils `timeout` exists, an outer
#   time limit.
# Each step prints a UTC timestamp, so a slow or stuck teardown shows in the job log where it stopped.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE_DIR="$(dirname "$SCRIPT_DIR")"
TARGET_DIR="$MODULE_DIR/target"

GRACE_SECONDS=20
COMPOSE_STOP_SECONDS=10
COMPOSE_LIMIT_SECONDS=90

timestamp() {
    date -u +%H:%M:%SZ
}

# Prints the PIDs of every process below the given PID, depth first.
descendants() {
    local child
    for child in $(pgrep -P "$1" 2>/dev/null); do
        echo "$child"
        descendants "$child"
    done
}

# Prints the PIDs of the given list that are still alive.
alive() {
    local pid
    for pid in "$@"; do
        if kill -0 "$pid" 2>/dev/null; then
            echo "$pid"
        fi
    done
}

echo "=== Stopping E2E Dev-UI Test Environment ($(timestamp)) ==="

# --- Step 1: Stop Quarkus Dev Mode ---
PID_FILE="$TARGET_DIR/quarkus-dev.pid"
if [ -f "$PID_FILE" ]; then
    QUARKUS_PID=$(cat "$PID_FILE")
    echo "[1/2] Stopping Quarkus dev mode (PID: $QUARKUS_PID) ($(timestamp))..."
    # The whole tree below this PID is signalled, so the file content must be a plain positive PID.
    # A value such as 0 or -1 would address a process group or every process the user may signal
    # (kill(2)), so anything else is refused rather than passed on.
    if [[ ! "$QUARKUS_PID" =~ ^[1-9][0-9]*$ ]]; then
        echo "[1/2] PID file does not hold a valid PID, refusing to signal anything"
    elif kill -0 "$QUARKUS_PID" 2>/dev/null; then
        # Collect the tree before signalling: once the parent exits, its children are re-parented
        # and can no longer be found through it.
        TREE="$(descendants "$QUARKUS_PID") $QUARKUS_PID"
        echo "[1/2] Process tree: $(echo $TREE)"
        # shellcheck disable=SC2086
        kill -TERM $TREE 2>/dev/null || true
        WAITED=0
        # shellcheck disable=SC2086
        while [ -n "$(alive $TREE)" ] && [ $WAITED -lt $GRACE_SECONDS ]; do
            sleep 1
            WAITED=$((WAITED + 1))
        done
        # shellcheck disable=SC2086
        SURVIVORS="$(alive $TREE)"
        if [ -n "$SURVIVORS" ]; then
            echo "[1/2] Still running after ${GRACE_SECONDS}s, force killing: $(echo $SURVIVORS)"
            # shellcheck disable=SC2086
            ps -o pid=,ppid=,etime=,args= -p "$(echo $SURVIVORS | tr ' ' ',')" 2>/dev/null || true
            # shellcheck disable=SC2086
            kill -9 $SURVIVORS 2>/dev/null || true
        fi
        echo "[1/2] Quarkus dev mode stopped after ${WAITED}s ($(timestamp))"
    else
        echo "[1/2] Quarkus dev mode not running (PID: $QUARKUS_PID)"
    fi
    rm -f "$PID_FILE"
else
    echo "[1/2] No PID file found, skipping Quarkus shutdown"
fi

# --- Step 2: Stop Keycloak ---
echo "[2/2] Stopping Keycloak ($(timestamp))..."
cd "$MODULE_DIR"
if command -v timeout >/dev/null 2>&1; then
    timeout --kill-after=10 "$COMPOSE_LIMIT_SECONDS" docker compose down --timeout "$COMPOSE_STOP_SECONDS" \
        || echo "[2/2] docker compose down returned non-zero (exit $?)"
else
    docker compose down --timeout "$COMPOSE_STOP_SECONDS" \
        || echo "[2/2] docker compose down returned non-zero (exit $?)"
fi

echo "=== E2E Dev-UI Test Environment Stopped ($(timestamp)) ==="
exit 0
