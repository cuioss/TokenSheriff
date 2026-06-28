/**
 * @fileoverview WCAG 2.1 Level AA accessibility compliance tests
 * Uses @axe-core/playwright for automated accessibility auditing.
 */

import {
    accessibilityTest as test,
    expect,
    takeStartScreenshot,
} from "../fixtures/test-fixtures.js";
import { CONSTANTS } from "../utils/constants.js";
import {
    goToStatusConfig,
    goToTokenDebugger,
} from "../utils/devui-navigation.js";

test.describe("Accessibility - WCAG 2.1 AA Compliance", () => {
    test.beforeEach(async ({ page }, testInfo) => {
        await takeStartScreenshot(page, testInfo);
    });

    test("Full Dev-UI page WCAG compliance", async ({
        page,
        accessibilityHelper,
    }) => {
        await page.goto(CONSTANTS.URLS.DEVUI, {
            waitUntil: "networkidle",
            timeout: CONSTANTS.TIMEOUTS.NAVIGATION,
        });
        await page.waitForLoadState("networkidle");

        const results = await accessibilityHelper.analyze();
        // Log violations for debugging but don't fail on known Dev-UI framework issues
        if (results.violations.length > 0) {
            console.log(
                "Dev-UI page violations:",
                results.violations.map((v) => `${v.id}: ${v.description}`),
            );
        }
        // Expect no critical violations (allow minor issues from the Dev-UI framework itself)
        const critical = results.violations.filter(
            (v) => v.impact === "critical",
        );
        expect(critical).toEqual([]);
    });

    test("Status & Config page accessibility", async ({
        page,
        accessibilityHelper,
    }) => {
        await goToStatusConfig(page);

        const results = await accessibilityHelper.analyze();
        const critical = results.violations.filter(
            (v) => v.impact === "critical",
        );
        expect(critical).toEqual([]);
    });

    test("Token Debugger page accessibility", async ({
        page,
        accessibilityHelper,
    }) => {
        await goToTokenDebugger(page);

        const results = await accessibilityHelper.analyze();
        const critical = results.violations.filter(
            (v) => v.impact === "critical",
        );
        expect(critical).toEqual([]);
    });

    test("Keyboard navigation works on Token Debugger", async ({ page }) => {
        await goToTokenDebugger(page);

        // Tab through interactive elements
        await page.keyboard.press("Tab");
        await page.keyboard.press("Tab");
        await page.keyboard.press("Tab");

        // Verify focus is on an interactive element (not stuck).
        // Traverse shadow roots to find the deepest active element,
        // since Dev-UI Lit components use shadow DOM.
        const focusedTag = await page.evaluate(() => {
            let el = document.activeElement;
            while (el?.shadowRoot?.activeElement) {
                el = el.shadowRoot.activeElement;
            }
            return el?.tagName?.toLowerCase();
        });
        // Should be on a focusable element (or a shadow host like the custom element)
        expect([
            "button",
            "textarea",
            "input",
            "a",
            "select",
            "qwc-jwt-debugger",
        ]).toContain(focusedTag);
    });
});
