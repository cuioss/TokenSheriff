/**
 * @fileoverview Service to obtain real JWT tokens from Keycloak
 * Uses client_credentials grant for test automation
 */

import { CONSTANTS } from "./constants.js";

/**
 * Fetch a JWT access token from Keycloak using client credentials grant
 *
 * @param {object} [options] - Configuration options
 * @param {string} [options.realm='benchmark'] - Keycloak realm
 * @param {string} [options.clientId] - OAuth client ID
 * @param {string} [options.clientSecret] - OAuth client secret
 * @returns {Promise<string>} The access token string
 */
export async function getKeycloakToken(options = {}) {
    const realm = options.realm || "benchmark";
    const authConfig =
        realm === "integration"
            ? CONSTANTS.AUTH.INTEGRATION
            : CONSTANTS.AUTH.BENCHMARK;

    const clientId = options.clientId || authConfig.CLIENT_ID;
    const clientSecret = options.clientSecret || authConfig.CLIENT_SECRET;

    const tokenUrl =
        realm === "integration"
            ? CONSTANTS.URLS.KEYCLOAK_INTEGRATION_TOKEN
            : CONSTANTS.URLS.KEYCLOAK_TOKEN;

    const body = new URLSearchParams({
        grant_type: "client_credentials",
        client_id: clientId,
        client_secret: clientSecret,
    });

    const response = await fetch(tokenUrl, {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: body.toString(),
        // Ignore self-signed certificate errors
        ...(typeof process !== "undefined" && {
            signal: AbortSignal.timeout(10_000),
        }),
    });

    if (!response.ok) {
        const text = await response.text();
        throw new Error(
            `Failed to get Keycloak token: ${response.status} ${text}`,
        );
    }

    const data = await response.json();
    return data.access_token;
}

/**
 * Fetch a JWT access token from Keycloak, tolerating self-signed cert errors.
 * Falls back to process-level NODE_TLS_REJECT_UNAUTHORIZED if fetch fails.
 *
 * @param {object} [options] - Same options as getKeycloakToken
 * @returns {Promise<string>} The access token string
 */
export async function getKeycloakTokenInsecure(options = {}) {
    // Node.js fetch respects NODE_TLS_REJECT_UNAUTHORIZED
    const oldTls = process.env.NODE_TLS_REJECT_UNAUTHORIZED;
    try {
        process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";
        return await getKeycloakToken(options);
    } finally {
        if (oldTls === undefined) {
            delete process.env.NODE_TLS_REJECT_UNAUTHORIZED;
        } else {
            process.env.NODE_TLS_REJECT_UNAUTHORIZED = oldTls;
        }
    }
}
