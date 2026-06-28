/**
 * Mock implementation of Lit library for testing
 *
 * This mock provides the essential Lit functionality needed for testing
 * without requiring the full Lit library to be loaded.
 */

// Helper function for template literal processing
const processTemplateLiteral = (strings, values) => {
  return strings.reduce((result, string, i) => {
    const value = values[i] ? values[i] : '';
    return result + string + value;
  }, '');
};

// Mock html template literal function
export const html = (strings, ...values) => {
  // Simple template interpolation for testing
  return processTemplateLiteral(strings, values);
};

// Mock css template literal function
export const css = (strings, ...values) => {
  // Simple CSS template interpolation
  return processTemplateLiteral(strings, values);
};

// Mock LitElement base class
export class LitElement {
  constructor() {
    // Create a mock element without extending HTMLElement
    this._properties = new Map();
    this._hasUpdated = false;
    this._updatePromise = null;
    this._renderRoot = null;

    // Mock shadow DOM
    this.shadowRoot = {
      innerHTML: '',
      querySelector: jest.fn(_selector => {
        // Mock element with all necessary DOM methods
        return {
          tagName: 'DIV',
          textContent: '',
          value: '',
          disabled: false,
          classList: {
            contains: jest.fn(),
            add: jest.fn(),
            remove: jest.fn(),
            toggle: jest.fn(),
          },
          click: jest.fn(),
          focus: jest.fn(),
          blur: jest.fn(),
          dispatchEvent: jest.fn(),
          addEventListener: jest.fn(),
          removeEventListener: jest.fn(),
          setAttribute: jest.fn(),
          getAttribute: jest.fn(),
          removeAttribute: jest.fn(),
          querySelectorAll: jest.fn(() => []),
          querySelector: jest.fn(),
          style: {},
        };
      }),
      querySelectorAll: jest.fn(() => []),
      appendChild: jest.fn(),
      removeChild: jest.fn(),
    };

    // Mock DOM methods
    this.appendChild = jest.fn();
    this.removeChild = jest.fn();
    this.parentNode = null;
    this.tagName = 'MOCK-ELEMENT';
  }

  // Static properties
  static properties = {};

  static styles = '';

  // Lifecycle methods
  connectedCallback() {
    this._requestUpdate();
  }

  disconnectedCallback() {
    // Cleanup logic
  }

  attributeChangedCallback(_name, _oldValue, _newValue) {
    this._requestUpdate();
  }

  // Property system
  _requestUpdate() {
    if (!this._updatePromise) {
      this._updatePromise = Promise.resolve().then(() => {
        this._updatePromise = null;
        this._performUpdate();
        return true;
      });
    }
    return this._updatePromise;
  }

  _performUpdate() {
    this._hasUpdated = true;
    const result = this.render();
    this._render(result);
  }

  _render(result) {
    // Store the rendered result for testing
    this._lastRenderedResult = typeof result === 'string' ? result : '';

    // Simple rendering - just set innerHTML of shadow root
    if (this.shadowRoot && typeof result === 'string') {
      this.shadowRoot.innerHTML = result;
    }
  }

  // Get the render root (shadow DOM)
  get renderRoot() {
    return this._renderRoot || this.shadowRoot;
  }

  // Update complete promise
  get updateComplete() {
    return this._updatePromise || Promise.resolve();
  }

  // Abstract render method to be implemented by subclasses
  render() {
    return html``;
  }

  // Property getter/setter helpers
  _getProperty(name) {
    return this._properties.get(name);
  }

  _setProperty(name, value) {
    const oldValue = this._properties.get(name);
    if (oldValue !== value) {
      this._properties.set(name, value);
      this._requestUpdate();
    }
  }

  // Request an update cycle
  requestUpdate() {
    return this._requestUpdate();
  }

  // Check if the component has updated
  get hasUpdated() {
    return this._hasUpdated;
  }
}

// Mock reactive property decorator
export const property = (options = {}) => {
  return (target, propertyKey) => {
    // Store property metadata
    if (!target.constructor.properties) {
      target.constructor.properties = {};
    }
    target.constructor.properties[propertyKey] = options;

    // Create getter/setter
    const descriptor = {
      get() {
        return this._getProperty(propertyKey);
      },
      set(value) {
        this._setProperty(propertyKey, value);
      },
      enumerable: true,
      configurable: true,
    };

    Object.defineProperty(target, propertyKey, descriptor);
  };
};

// Mock state decorator
export const state = () => property({ state: true });

// Mock query decorator
export const query = selector => {
  return (target, propertyKey) => {
    Object.defineProperty(target, propertyKey, {
      get() {
        return this.renderRoot ? this.renderRoot.querySelector(selector) : null;
      },
      enumerable: true,
      configurable: true,
    });
  };
};

// Mock queryAll decorator
export const queryAll = selector => {
  return (target, propertyKey) => {
    Object.defineProperty(target, propertyKey, {
      get() {
        return this.renderRoot ? this.renderRoot.querySelectorAll(selector) : [];
      },
      enumerable: true,
      configurable: true,
    });
  };
};

// Default export for convenience
export default {
  html,
  css,
  LitElement,
  property,
  state,
  query,
  queryAll,
};
