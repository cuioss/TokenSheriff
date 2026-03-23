#!/bin/bash

# Generate PNG images from PlantUML (.puml) files.
#
# PlantUML resolution order:
#   1. Local `plantuml` command (e.g. brew install plantuml)
#   2. Maven-downloaded JAR in target/ (project root)
#   3. Auto-download from GitHub Releases into target/
#
# The JAR is stored in ${PROJECT_ROOT}/target/ so it is ignored by git
# and cleaned by `mvn clean`.

set -euo pipefail
shopt -s lastpipe

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PLANTUML_DIR="$SCRIPT_DIR"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
TARGET_DIR="${PROJECT_ROOT}/target"

# Pinned version — update when newer features are needed
PLANTUML_VERSION="1.2025.2"
PLANTUML_JAR="${TARGET_DIR}/plantuml-${PLANTUML_VERSION}.jar"
PLANTUML_URL="https://github.com/plantuml/plantuml/releases/download/v${PLANTUML_VERSION}/plantuml-${PLANTUML_VERSION}.jar"

resolve_plantuml() {
    # 1. Local command (verify it actually works, not just exists)
    if command -v plantuml &> /dev/null && plantuml -version &> /dev/null; then
        echo "Using locally installed PlantUML: $(which plantuml)"
        PLANTUML_CMD="plantuml -tpng"
        return
    fi

    # 2/3. JAR in target/ (download if missing)
    mkdir -p "$TARGET_DIR"
    if [ ! -f "$PLANTUML_JAR" ]; then
        echo "Downloading PlantUML ${PLANTUML_VERSION} to ${PLANTUML_JAR}..."
        curl -fSL "$PLANTUML_URL" -o "$PLANTUML_JAR"
        echo "Download complete ($(du -h "$PLANTUML_JAR" | cut -f1))"
    else
        echo "Using cached PlantUML ${PLANTUML_VERSION}"
    fi
    PLANTUML_CMD="java -jar ${PLANTUML_JAR}"
}

resolve_plantuml

echo ""
echo "Processing PlantUML files in ${PLANTUML_DIR}:"
FAILED=0
find "$PLANTUML_DIR" -name "*.puml" | sort | while read -r puml_file; do
    basename="${puml_file##*/}"
    png_file="${puml_file%.puml}.png"
    echo -n "  ${basename} ... "
    output_and_error=$($PLANTUML_CMD "$puml_file" 2>&1)
    if [ $? -eq 0 ] && [ -f "$png_file" ]; then
        echo "ok"
    else
        echo "FAILED"
        if [ -n "$output_and_error" ]; then
            echo "--- PlantUML Output ---"
            echo "$output_and_error"
            echo "-----------------------"
        fi
        FAILED=1
    fi
done

echo ""
if [ "$FAILED" -eq 0 ]; then
    echo "PlantUML generation complete"
else
    echo "PlantUML generation complete (some files may have failed)"
    exit 1
fi
