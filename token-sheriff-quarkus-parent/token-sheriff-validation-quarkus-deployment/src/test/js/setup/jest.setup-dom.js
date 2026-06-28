/**
 * Jest DOM setup file
 *
 * This file is executed after Jest environment setup and provides
 * custom DOM matchers and utilities for testing web components.
 */

import '@testing-library/jest-dom';

// Custom matchers for web component testing
expect.extend({
  /**
   * Checks if an element has been defined as a custom element
   * @param received
   * @param expectedTagName
   */
  toBeDefinedAsCustomElement(received, expectedTagName) {
    const pass = customElements.get(expectedTagName) !== undefined;

    if (pass) {
      return {
        message: () => `expected ${expectedTagName} not to be defined as a custom element`,
        pass: true,
      };
    }

    return {
      message: () => `expected ${expectedTagName} to be defined as a custom element`,
      pass: false,
    };
  },

  /**
   * Checks if a LitElement has rendered specific content
   * @param received
   * @param expectedContent
   */
  toHaveRenderedContent(received, expectedContent) {
    // For mocked components, check the rendered result
    if (received._lastRenderedResult) {
      const pass = received._lastRenderedResult.includes(expectedContent);

      if (pass) {
        return {
          message: () => `expected element not to have rendered content "${expectedContent}"`,
          pass: true,
        };
      }

      return {
        message: () =>
          `expected element to have rendered content "${expectedContent}", but got "${received._lastRenderedResult}"`,
        pass: false,
      };
    }

    // Fallback to checking shadow DOM innerHTML
    const shadowRoot = received.shadowRoot || received.renderRoot;
    const content = shadowRoot ? shadowRoot.innerHTML : '';
    const pass = content && content.includes(expectedContent);

    if (pass) {
      return {
        message: () => `expected element not to have rendered content "${expectedContent}"`,
        pass: true,
      };
    }

    return {
      message: () =>
        `expected element to have rendered content "${expectedContent}", but got "${content}"`,
      pass: false,
    };
  },

  /**
   * Checks if a component has a specific CSS class in its shadow DOM
   * @param received
   * @param expectedClass
   */
  toHaveShadowClass(received, expectedClass) {
    // For mocked components, check the rendered HTML content
    if (received._lastRenderedResult) {
      const pass =
        received._lastRenderedResult.includes(`class="${expectedClass}"`) ||
        received._lastRenderedResult.includes(`class='${expectedClass}'`) ||
        received._lastRenderedResult.includes(` ${expectedClass} `) ||
        received._lastRenderedResult.includes(` ${expectedClass}"`) ||
        received._lastRenderedResult.includes(`"${expectedClass} `);

      if (pass) {
        return {
          message: () =>
            `expected element not to have class "${expectedClass}" in rendered content`,
          pass: true,
        };
      }

      return {
        message: () => `expected element to have class "${expectedClass}" in rendered content`,
        pass: false,
      };
    }

    // Fallback method
    return {
      message: () => 'Mock component should have _lastRenderedResult property',
      pass: false,
    };
  },
});

// Utility function to wait for web component updates
global.waitForComponentUpdate = async component => {
  if (component.updateComplete) {
    await component.updateComplete;
  }
  // Additional wait for any async operations
  await new Promise(resolve => setTimeout(resolve, 0));
};

// Utility function to create and register a test component
global.createTestComponent = (tagName, componentClass) => {
  if (!customElements.get(tagName)) {
    customElements.define(tagName, componentClass);
  }

  const element = document.createElement(tagName);
  document.body.append(element);

  return element;
};

// Cleanup function for test components
global.cleanupTestComponents = () => {
  // Remove all custom elements from document body
  const customElements = document.body.querySelectorAll('*');
  customElements.forEach(element => {
    if (element.tagName.includes('-')) {
      element.remove();
    }
  });
};

// Setup DOM cleanup after each test
afterEach(() => {
  // Reset all mocks
  jest.clearAllMocks();

  // Clean up test components
  global.cleanupTestComponents();

  // Reset fetch mock
  global.fetch.mockClear();
});
