#!/bin/bash
set -e

# Script to build native executable if it doesn't exist

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Check if native executable exists
RUNNER_FILE=$(find "${PROJECT_DIR}/target" -name "*-runner" -type f 2>/dev/null | head -n 1)

if [[ -z "$RUNNER_FILE" ]]; then
    echo "Native executable not found, building it now..."
    echo "This will take approximately 2 minutes..."
    
    # Find the root directory with mvnw
    ROOT_DIR=$(cd "${PROJECT_DIR}/../.." && pwd)
    cd "${ROOT_DIR}"

    # WHY -am: this script runs in a separate Maven process, which cannot see the artifacts
    # the calling build produced in its reactor. Built with "-pl <this module>" alone, the
    # upstream modules (token-sheriff-validation-quarkus, token-sheriff-client-quarkus, their
    # deployment modules, token-sheriff-validation, token-sheriff-client) are resolved from
    # the local Maven repository instead, that is, from whatever snapshot was installed or
    # downloaded last, and the native image silently tests that code rather than the
    # checked-out sources. -am packages the upstream modules from this checkout in the same
    # reactor, so the native image always contains the code under test. Do not drop it.
    #
    # The native build itself is bound to "package" in this module only, through
    # integration-tests.native-build.phase (see the integration-tests profile in pom.xml).
    # It is deliberately NOT a "quarkus:build" goal on the command line: with -am that goal
    # would also run against every upstream library and extension module. Tests, JaCoCo,
    # the npm lint/test executions and javadoc are skipped here; the outer lifecycle runs them.
    ./mvnw --no-transfer-progress -Pintegration-tests package \
        -pl token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests -am \
        -Dintegration-tests.native-build.phase=package \
        -DskipTests \
        -Djacoco.skip=true \
        -Dskip.npm \
        -Dskip.installnodenpm \
        -Dmaven.javadoc.skip=true
    
    # Verify it was created
    RUNNER_FILE=$(find "${PROJECT_DIR}/target" -name "*-runner" -type f 2>/dev/null | head -n 1)
    if [[ -z "$RUNNER_FILE" ]]; then
        echo "Error: Failed to build native executable"
        exit 1
    fi
    echo "Native executable built: $(basename "$RUNNER_FILE")"
else
    echo "Native executable already exists: $(basename "$RUNNER_FILE")"
fi