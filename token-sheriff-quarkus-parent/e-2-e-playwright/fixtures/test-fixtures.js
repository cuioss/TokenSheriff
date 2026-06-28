/**
 * @fileoverview Consolidated test fixtures for Token-Sheriff Dev-UI Playwright tests
 * Provides page fixtures with automatic logging and accessibility helpers
 */

import { mkdirSync } from "fs";
import { join } from "path";
import { test as base, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { testLogger } from "../utils/test-logger.js";
import { goToStatusConfig, goToTokenDebugger } from "../utils/devui-navigation.js";

/**
 * Extended test with logging and screenshot fixtures
 */
export const test = base.extend({
    /**
     * Page fixture with automatic console logging
     */
    page: async ({ page }, use, testInfo) => {
        const t0 = Date.now();
        testLogger.startTest(testInfo.testId);
        testLogger.setupBrowserCapture(page);
        testLogger.info(
            "Lifecycle",
            `START  ${testInfo.titlePath.join(" > ")}`,
        );

        await use(page);

        // Log test outcome
        const duration = ((Date.now() - t0) / 1000).toFixed(1);
        const status = testInfo.status ?? "unknown";
        testLogger.info(
            "Lifecycle",
            `END    ${status.toUpperCase()} (${duration}s) – ${testInfo.title}`,
        );
        if (testInfo.error) {
            testLogger.error("Lifecycle", testInfo.error.message);
        }

        // Automatic end-of-test screenshot and text logs
        mkdirSync(testInfo.outputDir, { recursive: true });
        await page
            .screenshot({
                path: join(testInfo.outputDir, "after.png"),
                fullPage: true,
            })
            .catch(() => {});
        testLogger.writeLogs(testInfo);
    },
});

/**
 * Accessibility-focused test fixture using @axe-core/playwright
 */
export const accessibilityTest = test.extend({
    /**
     * Run WCAG 2.1 AA accessibility check after each test
     */
    accessibilityHelper: async ({ page }, use) => {
        const helper = {
            /**
             * Run axe-core analysis on the current page
             * @param {object} [options] - Additional options
             * @param {string[]} [options.disableRules] - Rules to disable
             * @returns {Promise<import('axe-core').AxeResults>}
             */
            async analyze(options = {}) {
                const builder = new AxeBuilder({ page })
                    .withTags(["wcag2aa", "wcag21aa", "best-practice"])
                    .disableRules([
                        "bypass",
                        "landmark-one-main",
                        "region",
                        ...(options.disableRules || []),
                    ]);
                return builder.analyze();
            },

            /**
             * Assert no WCAG violations (or only acceptable ones)
             * @param {object} [options] - Options
             * @param {string[]} [options.disableRules] - Rules to disable
             */
            async expectNoViolations(options = {}) {
                const results = await this.analyze(options);
                testLogger.info(
                    "Accessibility",
                    `axe-core scan: ${results.passes.length} rules passed, ${results.violations.length} violations`,
                );
                if (results.violations.length > 0) {
                    const summary = results.violations
                        .map(
                            (v) =>
                                `${v.id}: ${v.description} (${v.nodes.length} elements)`,
                        )
                        .join("\n");
                    console.warn("Accessibility violations:\n" + summary);
                }
                expect(results.violations).toEqual([]);
            },
        };

        await use(helper);
    },
});

/**
 * Base serial test fixture sharing a single browser page across all tests in a worker.
 * Provides worker-scoped _sharedPage and per-test logging via the page override.
 */
const serialBaseTest = base.extend({
    /** Worker-scoped shared page, reused across all tests */
    _sharedPage: [
        async ({ browser }, use) => {
            const context = await browser.newContext();
            const page = await context.newPage();
            await use(page);
            await context.close();
        },
        { scope: "worker" },
    ],

    /** Override page to delegate to shared page with per-test logging */
    page: async ({ _sharedPage }, use, testInfo) => {
        const t0 = Date.now();
        testLogger.startTest(testInfo.testId);
        testLogger.setupBrowserCapture(_sharedPage);
        testLogger.info(
            "Lifecycle",
            `START  ${testInfo.titlePath.join(" > ")}`,
        );

        await use(_sharedPage);

        const duration = ((Date.now() - t0) / 1000).toFixed(1);
        const status = testInfo.status ?? "unknown";
        testLogger.info(
            "Lifecycle",
            `END    ${status.toUpperCase()} (${duration}s) – ${testInfo.title}`,
        );
        if (testInfo.error) {
            testLogger.error("Lifecycle", testInfo.error.message);
        }
        mkdirSync(testInfo.outputDir, { recursive: true });
        await _sharedPage
            .screenshot({
                path: join(testInfo.outputDir, "after.png"),
                fullPage: true,
            })
            .catch(() => {});
        testLogger.writeLogs(testInfo);
    },
});

/**
 * Serial test fixture for Status & Config page.
 * Navigates to the page once per worker via _statusConfigState.
 */
export const serialStatusConfigTest = serialBaseTest.extend({
    _statusConfigState: [
        async ({ _sharedPage }, use) => {
            await goToStatusConfig(_sharedPage);
            await use({ page: _sharedPage });
        },
        { scope: "worker" },
    ],
});

/**
 * Serial test fixture for Token Debugger page.
 * Navigates to the page once per worker via _tokenDebuggerState.
 */
export const serialTokenDebuggerTest = serialBaseTest.extend({
    _tokenDebuggerState: [
        async ({ _sharedPage }, use) => {
            await goToTokenDebugger(_sharedPage);
            await use({ page: _sharedPage });
        },
        { scope: "worker" },
    ],
});

export { expect } from "@playwright/test";
export { takeStartScreenshot } from "../utils/test-logger.js";
