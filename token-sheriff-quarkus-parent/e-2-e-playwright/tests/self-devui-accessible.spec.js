/**
 * @fileoverview Self-tests that validate the E2E test environment
 * These are gate-keeper tests: if they fail, the environment is not ready.
 */

import { test, expect, takeStartScreenshot } from '../fixtures/test-fixtures.js';
import { CONSTANTS } from '../utils/constants.js';
import { isDevUIAccessible, navigateToDevUIPage } from '../utils/devui-navigation.js';

test.describe('self-devui-accessible: Environment Validation', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    await takeStartScreenshot(page, testInfo);
  });

  test('Quarkus application is accessible', async ({ page }) => {
    const response = await page.goto(CONSTANTS.URLS.BASE, {
      waitUntil: 'domcontentloaded',
      timeout: CONSTANTS.TIMEOUTS.NAVIGATION,
    });
    expect(response).not.toBeNull();
    expect(response.status()).toBeLessThan(500);
  });

  test('Dev-UI is accessible', async ({ page }) => {
    const accessible = await isDevUIAccessible(page);
    expect(accessible).toBe(true);
  });

  test('Token-Sheriff extension is visible in Dev-UI', async ({ page }) => {
    await page.goto(CONSTANTS.URLS.DEVUI, {
      waitUntil: 'networkidle',
      timeout: CONSTANTS.TIMEOUTS.NAVIGATION,
    });

    // The extension card should mention Token-Sheriff
    await page.getByText('Token-Sheriff').first().waitFor({
      state: 'visible',
      timeout: CONSTANTS.TIMEOUTS.ELEMENT_VISIBLE,
    });
  });

  test('Status & Config page is navigable', async ({ page }) => {
    await navigateToDevUIPage(page, 'Status & Config');
    await expect(page.locator('qwc-jwt-status-config')).toBeAttached();
  });

  test('Token Debugger page is navigable', async ({ page }) => {
    await navigateToDevUIPage(page, 'Token Debugger');
    await expect(page.locator('qwc-jwt-debugger')).toBeAttached();
  });

  test('JSON-RPC returns runtime data (not BUILD_TIME)', async ({ page }) => {
    // Navigate to Dev-UI to establish the JSON-RPC WebSocket connection.
    await page.goto(CONSTANTS.URLS.DEVUI, {
      waitUntil: 'networkidle',
      timeout: CONSTANTS.TIMEOUTS.NAVIGATION,
    });

    // Wait for extension metadata to load (confirms WebSocket is connected
    // and SPA is fully initialized).
    await page.getByText('Token-Sheriff').first().waitFor({
      state: 'visible',
      timeout: CONSTANTS.TIMEOUTS.ELEMENT_VISIBLE,
    });

    // Call the JSON-RPC method directly via WebSocket from browser context.
    const result = await page.evaluate(() => {
      return new Promise((resolve, reject) => {
        const wsUrl = `wss://${location.host}/q/dev-ui/json-rpc-ws`;
        const ws = new WebSocket(wsUrl);
        ws.onopen = () => {
          ws.send(
            JSON.stringify({
              jsonrpc: '2.0',
              method: 'TokenSheriffDevUI.getValidationStatus',
              params: {},
              id: 1,
            }),
          );
        };
        ws.onmessage = (event) => {
          ws.close();
          resolve(event.data);
        };
        ws.onerror = () => reject(new Error('WebSocket connection failed'));
        setTimeout(() => {
          ws.close();
          reject(new Error('JSON-RPC timeout'));
        }, 10000);
      });
    });

    // Verify the response does not contain BUILD_TIME placeholder data
    expect(result).not.toContain('BUILD_TIME');
  });
});
