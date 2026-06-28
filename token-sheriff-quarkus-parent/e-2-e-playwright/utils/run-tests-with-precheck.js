/**
 * @fileoverview Run Playwright tests with a self-test gate check.
 * Self-tests must pass before functional/accessibility tests are run.
 */

import { execSync } from "child_process";

const SELF_TEST_PATTERN = "tests/self-*.spec.js";

function run(command, label) {
    console.log(`\n--- ${label} ---`);
    try {
        execSync(command, { stdio: "inherit" });
        return true;
    } catch {
        return false;
    }
}

// Step 1: Run self-tests
const selfTestsPassed = run(
    `npx playwright test ${SELF_TEST_PATTERN} --reporter=list`,
    "Self-Tests (Gate Check)",
);

if (!selfTestsPassed) {
    console.error(
        "\nSelf-tests failed. Environment is not ready. Skipping functional tests.",
    );
    process.exit(1);
}

// Step 2: Run functional tests (exclude self- and accessibility)
const args = process.argv.slice(2);
const testPattern =
    args.length > 0 ? args.join(" ") : '--grep-invert "self-|accessibility"';

const functionalPassed = run(
    `npx playwright test ${testPattern} --reporter=list`,
    "Functional Tests",
);

if (!functionalPassed) {
    console.error("\nFunctional tests failed.");
    process.exit(1);
}

console.log("\nAll tests passed.");
