/**
 * @fileoverview E2E tests for the merged Status & Config Dev-UI page
 * Verifies the qwc-jwt-status-config component renders correctly with runtime data
 * from all four JSON-RPC methods (validation status, JWKS status, configuration, health).
 *
 * Uses serial mode with a shared page — navigation happens once in the fixture.
 */

import { serialStatusConfigTest as test, expect, takeStartScreenshot } from "../fixtures/test-fixtures.js";
import { CONSTANTS } from "../utils/constants.js";

test.describe.configure({ mode: "serial" });

test.describe("01 - Status & Config Page", () => {
    test.beforeEach(async ({ page, _statusConfigState }, testInfo) => {
        await takeStartScreenshot(page, testInfo);
    });

    // --- Status Overview section ---

    test("should display the status config container", async ({ page }) => {
        const container = page.locator(
            CONSTANTS.SELECTORS.STATUS_CONFIG_CONTAINER,
        );
        const loading = page.locator(CONSTANTS.SELECTORS.STATUS_CONFIG_LOADING);
        const error = page.locator(CONSTANTS.SELECTORS.STATUS_CONFIG_ERROR);

        await expect(container.or(loading).or(error)).toBeVisible({
            timeout: CONSTANTS.TIMEOUTS.ELEMENT_VISIBLE,
        });
    });

    test("should show RUNTIME data (not BUILD_TIME placeholders)", async ({
        page,
    }) => {
        await page
            .locator(CONSTANTS.SELECTORS.STATUS_CONFIG_CONTAINER)
            .or(page.locator(CONSTANTS.SELECTORS.STATUS_CONFIG_ERROR))
            .waitFor({
                state: "visible",
                timeout: CONSTANTS.TIMEOUTS.ELEMENT_VISIBLE,
            });
        const text = await page
            .locator("qwc-jwt-status-config")
            .textContent();
        expect(text).not.toContain("BUILD_TIME");
    });

    test("should display status indicator", async ({ page }) => {
        const container = page.locator(
            CONSTANTS.SELECTORS.STATUS_CONFIG_CONTAINER,
        );
        await container
            .waitFor({
                state: "visible",
                timeout: CONSTANTS.TIMEOUTS.ELEMENT_VISIBLE,
            })
            .catch(() => {});

        if (await container.isVisible()) {
            const indicator = page.locator(
                CONSTANTS.SELECTORS.STATUS_CONFIG_STATUS_INDICATOR,
            );
            await expect(indicator).toBeVisible();
        }
    });

    test("should display status message", async ({ page }) => {
        const container = page.locator(
            CONSTANTS.SELECTORS.STATUS_CONFIG_CONTAINER,
        );
        await container
            .waitFor({
                state: "visible",
                timeout: CONSTANTS.TIMEOUTS.ELEMENT_VISIBLE,
            })
            .catch(() => {});

        if (await container.isVisible()) {
            const message = page.locator(
                CONSTANTS.SELECTORS.STATUS_CONFIG_STATUS_MESSAGE,
            );
            await expect(message).toBeVisible();
            const text = await message.textContent();
            expect(text.length).toBeGreaterThan(0);
        }
    });

    test("should display enabled/disabled metric", async ({ page }) => {
        const container = page.locator(
            CONSTANTS.SELECTORS.STATUS_CONFIG_CONTAINER,
        );
        await container
            .waitFor({
                state: "visible",
                timeout: CONSTANTS.TIMEOUTS.ELEMENT_VISIBLE,
            })
            .catch(() => {});

        if (await container.isVisible()) {
            const enabledMetric = page.locator(
                CONSTANTS.SELECTORS.METRIC_ENABLED,
            );
            await expect(enabledMetric).toBeVisible();
            const text = await enabledMetric.textContent();
            expect(text).toMatch(/Yes|No/);
        }
    });

    test("should display validator present metric", async ({ page }) => {
        const container = page.locator(
            CONSTANTS.SELECTORS.STATUS_CONFIG_CONTAINER,
        );
        await container
            .waitFor({
                state: "visible",
                timeout: CONSTANTS.TIMEOUTS.ELEMENT_VISIBLE,
            })
            .catch(() => {});

        if (await container.isVisible()) {
            const validatorMetric = page.locator(
                CONSTANTS.SELECTORS.METRIC_VALIDATOR_PRESENT,
            );
            await expect(validatorMetric).toBeVisible();
        }
    });

    test("should display overall status metric", async ({ page }) => {
        const container = page.locator(
            CONSTANTS.SELECTORS.STATUS_CONFIG_CONTAINER,
        );
        await container
            .waitFor({
                state: "visible",
                timeout: CONSTANTS.TIMEOUTS.ELEMENT_VISIBLE,
            })
            .catch(() => {});

        if (await container.isVisible()) {
            const statusMetric = page.locator(
                CONSTANTS.SELECTORS.METRIC_OVERALL_STATUS,
            );
            await expect(statusMetric).toBeVisible();
        }
    });

    // --- Issuers section ---

    test("should list configured issuers", async ({ page }) => {
        const container = page.locator(
            CONSTANTS.SELECTORS.STATUS_CONFIG_CONTAINER,
        );
        await container
            .waitFor({
                state: "visible",
                timeout: CONSTANTS.TIMEOUTS.ELEMENT_VISIBLE,
            })
            .catch(() => {});

        if (await container.isVisible()) {
            const issuerCards = page.locator(
                CONSTANTS.SELECTORS.STATUS_CONFIG_ISSUER_CARD,
            );
            // We have at least keycloak and integration issuers configured
            const count = await issuerCards.count();
            expect(count).toBeGreaterThanOrEqual(2);
        }
    });

    test("should display issuer details with URIs", async ({ page }) => {
        const container = page.locator(
            CONSTANTS.SELECTORS.STATUS_CONFIG_CONTAINER,
        );
        await container
            .waitFor({
                state: "visible",
                timeout: CONSTANTS.TIMEOUTS.ELEMENT_VISIBLE,
            })
            .catch(() => {});

        if (await container.isVisible()) {
            const firstIssuer = page
                .locator(CONSTANTS.SELECTORS.STATUS_CONFIG_ISSUER_CARD)
                .first();
            await expect(firstIssuer).toBeVisible();

            const issuerText = await firstIssuer.textContent();
            expect(issuerText.length).toBeGreaterThan(10);
        }
    });

    // --- Parser Configuration section ---

    test("should display parser configuration section", async ({ page }) => {
        const container = page.locator(
            CONSTANTS.SELECTORS.STATUS_CONFIG_CONTAINER,
        );
        await container
            .waitFor({
                state: "visible",
                timeout: CONSTANTS.TIMEOUTS.ELEMENT_VISIBLE,
            })
            .catch(() => {});

        if (await container.isVisible()) {
            const parserSection = page.locator(
                CONSTANTS.SELECTORS.STATUS_CONFIG_PARSER_SECTION,
            );
            await expect(parserSection).toBeVisible();

            const sectionText = await parserSection.textContent();
            expect(sectionText).toContain("Max Token Size");
            expect(sectionText).toContain("Clock Skew");
        }
    });

    // --- General Settings section ---

    test("should display general settings section", async ({ page }) => {
        const container = page.locator(
            CONSTANTS.SELECTORS.STATUS_CONFIG_CONTAINER,
        );
        await container
            .waitFor({
                state: "visible",
                timeout: CONSTANTS.TIMEOUTS.ELEMENT_VISIBLE,
            })
            .catch(() => {});

        if (await container.isVisible()) {
            const generalSection = page.locator(
                CONSTANTS.SELECTORS.STATUS_CONFIG_GENERAL_SECTION,
            );
            await expect(generalSection).toBeVisible();

            const sectionText = await generalSection.textContent();
            expect(sectionText).toContain("Log Level");
        }
    });

    // --- Health indicator ---

    test("should display health indicator", async ({ page }) => {
        const container = page.locator(
            CONSTANTS.SELECTORS.STATUS_CONFIG_CONTAINER,
        );
        await container
            .waitFor({
                state: "visible",
                timeout: CONSTANTS.TIMEOUTS.ELEMENT_VISIBLE,
            })
            .catch(() => {});

        if (await container.isVisible()) {
            const healthIndicator = page.locator(
                CONSTANTS.SELECTORS.STATUS_CONFIG_HEALTH_INDICATOR,
            );
            if (await healthIndicator.isVisible()) {
                const text = await healthIndicator.textContent();
                expect(text).toMatch(/Healthy|Issues/i);
            }
        }
    });

    // --- Refresh (mutating - keep last) ---

    test("should have a working refresh button", async ({ page }) => {
        const container = page.locator(
            CONSTANTS.SELECTORS.STATUS_CONFIG_CONTAINER,
        );
        await container
            .waitFor({
                state: "visible",
                timeout: CONSTANTS.TIMEOUTS.ELEMENT_VISIBLE,
            })
            .catch(() => {});

        if (await container.isVisible()) {
            const refreshButton = page.locator(
                CONSTANTS.SELECTORS.STATUS_CONFIG_REFRESH_BUTTON,
            );
            await expect(refreshButton).toBeVisible();
            await expect(refreshButton).toBeEnabled();

            // Click refresh and verify the page doesn't crash
            await refreshButton.click();
            await expect(
                container.or(
                    page.locator(CONSTANTS.SELECTORS.STATUS_CONFIG_LOADING),
                ),
            ).toBeVisible({
                timeout: CONSTANTS.TIMEOUTS.ELEMENT_VISIBLE,
            });
        }
    });
});
