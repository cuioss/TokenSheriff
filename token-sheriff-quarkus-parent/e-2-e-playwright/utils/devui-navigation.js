/**
 * @fileoverview Dev-UI navigation helpers for Playwright tests
 * Handles navigation to Quarkus Dev-UI pages with shadow DOM awareness.
 *
 * Navigation strategy: Click-based navigation through the extension card.
 * Quarkus Dev-UI is a SPA (Vaadin Router + Lit web components) where extension
 * sub-page routes are registered lazily. Direct URL navigation is fragile because
 * the exact route paths depend on internal Quarkus conventions. Instead, we:
 * 1. Navigate to the Dev-UI extensions page
 * 2. Wait for the Token-Sheriff extension card to render
 * 3. Click the page link anchor inside the matching qwc-extension-link element
 * 4. Wait for the custom element to appear in the #page outlet
 *
 * DOM structure of extension card page links:
 *   <qwc-extension-link displayName="Title" path="/q/dev-ui/...">
 *     #shadow-root
 *       <a class="extensionLink" href="/q/dev-ui/...">
 *         <span class="iconAndName"><vaadin-icon/> Title</span>
 *       </a>
 *       <qui-badge><span>Label</span></qui-badge>
 *
 * The <a> element is inside the shadow root. We must click the <a> element
 * (not the badge) to trigger Vaadin Router navigation.
 * We scope to <qwc-extension-link> elements to avoid matching sidebar items.
 */

import { CONSTANTS } from './constants.js';
import { testLogger } from './test-logger.js';

/**
 * Pages configuration mapping page title to the custom element tag name.
 * The page title matches the `displayName` attribute of `qwc-extension-link`.
 */
const PAGES = {
  'Status & Config': 'qwc-jwt-status-config',
  'Token Debugger': 'qwc-jwt-debugger',
};

/**
 * Navigate to a Dev-UI extension sub-page by clicking through the extension card.
 *
 * @param {import('@playwright/test').Page} page - Playwright page
 * @param {string} pageTitle - The page title as set by the processor (e.g. "Status & Config")
 * @param {string} [waitForSelector] - Optional CSS selector to wait for after navigation
 */
export async function navigateToDevUIPage(page, pageTitle, waitForSelector) {
  const elementName = PAGES[pageTitle];
  if (!elementName) {
    throw new Error(`Unknown page: ${pageTitle}. Valid pages: ${Object.keys(PAGES).join(', ')}`);
  }

  testLogger.info('Navigation', `Navigating to Dev-UI page "${pageTitle}"`);

  // Step 1: Navigate to Dev-UI extensions page
  await page.goto(CONSTANTS.URLS.DEVUI, {
    waitUntil: 'domcontentloaded',
    timeout: CONSTANTS.TIMEOUTS.NAVIGATION,
  });

  // Step 2: Wait for the Token-Sheriff extension card to appear.
  await page.getByText('Token-Sheriff').first().waitFor({
    state: 'visible',
    timeout: CONSTANTS.TIMEOUTS.ELEMENT_VISIBLE,
  });

  // Step 3: Click the page link.
  // Find the <qwc-extension-link> that contains our page title text,
  // then click the <a> anchor inside it. Using qwc-extension-link as scope
  // avoids matching sidebar items like "Configuration".
  const extensionLink = page.locator('qwc-extension-link').filter({ hasText: pageTitle });
  await extensionLink.first().waitFor({
    state: 'visible',
    timeout: CONSTANTS.TIMEOUTS.SHORT,
  });
  // Click the anchor element inside the shadow root
  await extensionLink.first().locator('a').first().click();

  // Step 4: Wait for the custom element to appear in the DOM.
  try {
    await page.locator(elementName).first().waitFor({
      state: 'attached',
      timeout: CONSTANTS.TIMEOUTS.ELEMENT_VISIBLE,
    });
  } catch (err) {
    // Dump diagnostic information before failing
    const diag = await page.evaluate(() => {
      const outlet = document.querySelector('#page');
      return {
        currentUrl: window.location.href,
        outletExists: !!outlet,
        outletChildCount: outlet?.children?.length ?? 0,
        outletChildTags: Array.from(outlet?.children ?? []).map((c) => c.tagName.toLowerCase()),
        outletInnerHTML: outlet?.innerHTML?.substring(0, 1000) ?? '',
      };
    });
    console.error(`[devui-nav] Element <${elementName}> not found. Diagnostics:`, JSON.stringify(diag, null, 2));
    throw err;
  }
  testLogger.info('Navigation', `Page "${pageTitle}" ready (<${elementName}> attached)`);

  if (waitForSelector) {
    await page.locator(waitForSelector).waitFor({
      state: 'visible',
      timeout: CONSTANTS.TIMEOUTS.ELEMENT_VISIBLE,
    });
  }
}

/**
 * Navigate to the Status & Config page
 * @param {import('@playwright/test').Page} page
 */
export async function goToStatusConfig(page) {
  await navigateToDevUIPage(page, 'Status & Config');
}

/**
 * Navigate to the Token Debugger page
 * @param {import('@playwright/test').Page} page
 */
export async function goToTokenDebugger(page) {
  await navigateToDevUIPage(page, 'Token Debugger');
}

/**
 * Check if the Dev-UI is accessible
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<boolean>}
 */
export async function isDevUIAccessible(page) {
  try {
    const response = await page.goto(CONSTANTS.URLS.DEVUI, {
      waitUntil: 'domcontentloaded',
      timeout: CONSTANTS.TIMEOUTS.NAVIGATION,
    });
    return response !== null && response.ok();
  } catch {
    return false;
  }
}
