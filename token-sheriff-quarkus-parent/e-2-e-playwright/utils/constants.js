/**
 * @fileoverview Constants for Token-Sheriff Dev-UI E2E tests
 */

const BASE_URL = process.env.PLAYWRIGHT_BASE_URL || "https://localhost:8443";
const KEYCLOAK_URL =
    process.env.PLAYWRIGHT_KEYCLOAK_URL || "https://localhost:1443";

export const CONSTANTS = {
    URLS: {
        BASE: BASE_URL,
        DEVUI: `${BASE_URL}/q/dev-ui/`,
        DEVUI_EXTENSIONS: `${BASE_URL}/q/dev-ui/extensions`,
        HEALTH: `${BASE_URL}/q/health`,
        HEALTH_READY: `${BASE_URL}/q/health/ready`,
        KEYCLOAK: KEYCLOAK_URL,
        KEYCLOAK_TOKEN: `${KEYCLOAK_URL}/realms/benchmark/protocol/openid-connect/token`,
        KEYCLOAK_INTEGRATION_TOKEN: `${KEYCLOAK_URL}/realms/integration/protocol/openid-connect/token`,
    },

    /** Dev-UI navigation paths for Token-Sheriff extension pages.
     *  Namespace is the Maven artifactId; slugs are derived from page titles. */
    DEVUI_PAGES: {
        STATUS_CONFIG: `${BASE_URL}/q/dev-ui/token-sheriff-validation-quarkus/status-config`,
        TOKEN_DEBUGGER: `${BASE_URL}/q/dev-ui/token-sheriff-validation-quarkus/token-debugger`,
    },

    /** data-testid selectors for Playwright locators */
    SELECTORS: {
        // Status & Config (merged)
        STATUS_CONFIG_CONTAINER: '[data-testid="status-config-container"]',
        STATUS_CONFIG_HEALTH_INDICATOR: '[data-testid="status-config-health-indicator"]',
        STATUS_CONFIG_REFRESH_BUTTON: '[data-testid="status-config-refresh-button"]',
        STATUS_CONFIG_STATUS_INDICATOR: '[data-testid="status-config-status-indicator"]',
        STATUS_CONFIG_STATUS_MESSAGE: '[data-testid="status-config-status-message"]',
        STATUS_CONFIG_LOADING: '[data-testid="status-config-loading"]',
        STATUS_CONFIG_ERROR: '[data-testid="status-config-error"]',
        STATUS_OVERVIEW_SECTION: '[data-testid="status-overview-section"]',
        STATUS_CONFIG_ISSUERS_SECTION: '[data-testid="status-config-issuers-section"]',
        STATUS_CONFIG_ISSUER_CARD: '[data-testid="status-config-issuer-card"]',
        STATUS_CONFIG_PARSER_SECTION: '[data-testid="status-config-parser-section"]',
        STATUS_CONFIG_HTTP_SECTION: '[data-testid="status-config-http-section"]',
        STATUS_CONFIG_GENERAL_SECTION: '[data-testid="status-config-general-section"]',
        METRIC_ENABLED: '[data-testid="metric-enabled"]',
        METRIC_VALIDATOR_PRESENT: '[data-testid="metric-validator-present"]',
        METRIC_OVERALL_STATUS: '[data-testid="metric-overall-status"]',

        // Token Debugger
        JWT_DEBUGGER_CONTAINER: '[data-testid="jwt-debugger-container"]',
        JWT_DEBUGGER_TOKEN_INPUT: '[data-testid="jwt-debugger-token-input"]',
        JWT_DEBUGGER_VALIDATE_BUTTON:
            '[data-testid="jwt-debugger-validate-button"]',
        JWT_DEBUGGER_CLEAR_BUTTON: '[data-testid="jwt-debugger-clear-button"]',
        JWT_DEBUGGER_SAMPLE_BUTTON:
            '[data-testid="jwt-debugger-sample-button"]',
        JWT_DEBUGGER_RESULT: '[data-testid="jwt-debugger-result"]',
        JWT_DEBUGGER_RESULT_TITLE: '[data-testid="jwt-debugger-result-title"]',
        JWT_DEBUGGER_CLAIMS: '[data-testid="jwt-debugger-claims"]',
    },

    /** Keycloak authentication credentials */
    AUTH: {
        BENCHMARK: {
            CLIENT_ID: "benchmark-client",
            CLIENT_SECRET: "benchmark-secret",
            REALM: "benchmark",
        },
        INTEGRATION: {
            CLIENT_ID: "integration-client",
            CLIENT_SECRET: "integration-secret",
            REALM: "integration",
        },
    },

    /** Timeouts */
    TIMEOUTS: {
        NAVIGATION: 30_000,
        ELEMENT_VISIBLE: 15_000,
        JSON_RPC: 10_000,
        SHORT: 5_000,
    },
};
