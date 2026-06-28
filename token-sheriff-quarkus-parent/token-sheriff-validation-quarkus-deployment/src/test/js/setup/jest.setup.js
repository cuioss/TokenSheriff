/**
 * Jest global setup file
 *
 * This file is executed before all tests run and sets up global mocks
 * and environment configurations for the test suite.
 */

// Mock global console methods to reduce test noise
global.console = {
  ...console,
  warn: jest.fn(),
  error: jest.fn(),
  debug: jest.fn(),
};

// Mock window object properties commonly used in DevUI components
// Proper fix for jest-environment-jsdom ^30.0.4 to prevent navigation warnings
// Save the original location object to preserve its property descriptors
const originalLocation = window.location;

// Delete the existing location property and recreate it properly
delete window.location;
window.location = Object.defineProperties(
  {},
  {
    // Copy all original property descriptors to maintain jsdom compatibility
    ...Object.getOwnPropertyDescriptors(originalLocation),
    // Override specific properties with our test values
    href: {
      configurable: true,
      enumerable: true,
      get: () => 'http://localhost:8080/q/dev-ui',
      set: () => {}, // No-op setter to prevent navigation
    },
    hostname: {
      configurable: true,
      enumerable: true,
      get: () => 'localhost',
      set: () => {},
    },
    port: {
      configurable: true,
      enumerable: true,
      get: () => '8080',
      set: () => {},
    },
    protocol: {
      configurable: true,
      enumerable: true,
      get: () => 'http:',
      set: () => {},
    },
    pathname: {
      configurable: true,
      enumerable: true,
      get: () => '/q/dev-ui',
      set: () => {},
    },
    search: {
      configurable: true,
      enumerable: true,
      get: () => '',
      set: () => {},
    },
    hash: {
      configurable: true,
      enumerable: true,
      get: () => '',
      set: () => {},
    },
    origin: {
      configurable: true,
      enumerable: true,
      get: () => 'http://localhost:8080',
      set: () => {},
    },
    // Mock navigation methods to prevent "Not implemented" errors
    assign: {
      configurable: true,
      enumerable: true,
      value: jest.fn(),
    },
    replace: {
      configurable: true,
      enumerable: true,
      value: jest.fn(),
    },
    reload: {
      configurable: true,
      enumerable: true,
      value: jest.fn(),
    },
  }
);

// Mock fetch API for HTTP requests
global.fetch = jest.fn(() =>
  Promise.resolve({
    ok: true,
    status: 200,
    json: () => Promise.resolve({}),
    text: () => Promise.resolve(''),
    headers: new Headers(),
  })
);

// Mock customElements registry for web components
const definedElements = new Map();
global.customElements = {
  define: jest.fn((name, constructor) => {
    definedElements.set(name, constructor);
  }),
  get: jest.fn(name => definedElements.get(name)),
  whenDefined: jest.fn(() => Promise.resolve()),
};

// Mock DevUI global variables that might be injected by Quarkus
global.devUI = {
  jsonRPC: {
    CuiJwtDevUI: {
      getValidationStatus: jest.fn(() =>
        Promise.resolve({
          enabled: false,
          validatorPresent: false,
          status: 'BUILD_TIME',
          statusMessage: 'JWT validation status will be available at runtime',
        })
      ),
      getJwksStatus: jest.fn(() =>
        Promise.resolve({
          status: 'BUILD_TIME',
          message: 'JWKS endpoint status will be available at runtime',
        })
      ),
      getConfiguration: jest.fn(() =>
        Promise.resolve({
          enabled: false,
          healthEnabled: false,
          buildTime: true,
          message: 'Configuration details will be available at runtime',
        })
      ),
      validateToken: jest.fn(() =>
        Promise.resolve({
          valid: false,
          error: 'Token validation not available at build time',
        })
      ),
      getHealthInfo: jest.fn(() =>
        Promise.resolve({
          configurationValid: true,
          healthStatus: 'DOWN',
          message: 'Health information will be available at runtime',
        })
      ),
    },
  },
};

// Mock CSS custom properties for styling tests
document.documentElement.style.setProperty('--lumo-base-color', '#ffffff');
document.documentElement.style.setProperty('--lumo-contrast-10pct', 'rgba(0, 0, 0, 0.1)');
document.documentElement.style.setProperty('--lumo-primary-color', '#1976d2');
document.documentElement.style.setProperty('--lumo-success-color', '#4caf50');
document.documentElement.style.setProperty('--lumo-error-color', '#f44336');
document.documentElement.style.setProperty('--lumo-warning-color', '#ff9800');
