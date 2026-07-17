#!/bin/bash
# Stop JWT Integration Tests Docker containers

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# shellcheck source=lib-docker-compose.sh
source "${SCRIPT_DIR}/lib-docker-compose.sh"

echo "Stopping JWT Integration Tests Docker containers"

cd "${PROJECT_DIR}"

# This runs at Maven's pre-clean phase as a best-effort cleanup. If Docker isn't
# available or the daemon isn't running there is nothing to stop, so exit
# cleanly rather than failing `clean install`.
COMPOSE_BASE="$(resolve_compose_cmd || true)"
if [[ -z "$COMPOSE_BASE" ]]; then
    echo "Docker Compose not available — nothing to stop, skipping cleanup."
    exit 0
fi
if ! docker_daemon_up; then
    echo "Docker daemon not running — nothing to stop, skipping cleanup."
    exit 0
fi

# Use the docker-compose.yml file (only file available)
COMPOSE_FILE="docker-compose.yml"
MODE="native"

# Build compose command with optional overlay (using array for safe argument handling)
read -ra COMPOSE_CMD <<< "$COMPOSE_BASE"
COMPOSE_CMD+=("-f" "$COMPOSE_FILE")
if [[ -n "$COMPOSE_OVERRIDE" ]]; then
    echo "Using compose overlay: $COMPOSE_OVERRIDE"
    COMPOSE_CMD+=("-f" "$COMPOSE_OVERRIDE")
fi

# Stop and remove containers
echo "Stopping Docker containers ($MODE mode)..."
"${COMPOSE_CMD[@]}" down

# Optional: Clean up images and volumes
if [ "$1" = "--clean" ]; then
    echo "Cleaning up Docker images and volumes..."
    "${COMPOSE_CMD[@]}" down --volumes --rmi all
fi

echo "JWT Integration Tests stopped successfully"

# Show final status
if "${COMPOSE_CMD[@]}" ps | grep -q "Up"; then
    echo "Warning: Some containers are still running:"
    "${COMPOSE_CMD[@]}" ps
else
    echo "All containers are stopped"
fi