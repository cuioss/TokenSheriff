/*
 * Copyright © 2025-present CUI-OpenSource-Software (info@cuioss.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.cuioss.sheriff.token.client.discovery;

import com.dslplatform.json.DslJson;
import de.cuioss.http.client.handler.HttpHandler;
import de.cuioss.http.client.handler.HttpStatusFamily;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.client.internal.BackChannelHttp;
import de.cuioss.sheriff.token.client.internal.ClientLogMessages;
import de.cuioss.sheriff.token.client.internal.LogSanitizer;
import de.cuioss.sheriff.token.commons.error.TransportException;
import de.cuioss.sheriff.token.commons.transport.ParserConfig;
import de.cuioss.tools.logging.CuiLogger;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves an authorization server's endpoints and capabilities from its OpenID Connect
 * {@code .well-known/openid-configuration} discovery document.
 * <p>
 * The resolver composes the commons-blessed cui-http {@link HttpHandler} — the same
 * TLS/SSRF-hardened transport primitive the {@code commons} layer uses — to fetch the discovery
 * document, then maps it into an immutable {@link ProviderMetadata} via DSL-JSON. It adds no new
 * transport control of its own; the security posture is inherited from {@code HttpHandler}.
 * <p>
 * The commons {@code HttpWellKnownResolver} only surfaces {@code issuer} and {@code jwks_uri},
 * whereas the client flows need the full endpoint set and capability flags; this resolver
 * therefore parses the document directly into the richer {@link ProviderMetadata} record, reusing
 * the commons {@link ParserConfig} DSL-JSON instance (secure limits, native-image friendly).
 * <p>
 * The response is bounded during the read by
 * {@link ClientConfiguration#getDiscoveryDocumentMaxSize()} — the discovery document's own ceiling,
 * settable per client.
 * <p>
 * <strong>Security:</strong> a non-TLS issuer is rejected unless
 * {@link ClientConfiguration#allowInsecureHttp} is set; the discovery document's
 * {@code issuer} must match the configured issuer (OpenID Connect Discovery §4.3, a mix-up
 * precaution); an {@code https} issuer's document must advertise only {@code https} endpoints, a
 * refusal {@link ClientConfiguration#allowInsecureHttp} does not relax; the discovery response must
 * carry the {@code application/json} media type (parameters such as {@code charset} allowed), so an
 * absent or other {@code Content-Type} — {@code application/jrd+json} included — is refused; and the
 * availability of PKCE {@code S256} is recorded so the interactive
 * {@code authorization_code} flow can refuse a downgrade ({@code CLIENT-2}).
 *
 * @since 1.0
 * @author Oliver Wolff
 * @see <a href="https://openid.net/specs/openid-connect-discovery-1_0.html">OpenID Connect Discovery</a>
 */
public class DiscoveryResolver {

    private static final CuiLogger LOGGER = new CuiLogger(DiscoveryResolver.class);

    private static final String WELL_KNOWN_SUFFIX = "/.well-known/openid-configuration";
    private static final String SCHEME_HTTPS = "https";
    private static final String HTTPS_PREFIX = SCHEME_HTTPS + "://";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String JSON_MEDIA_TYPE = "application/json";

    /**
     * Human-readable provenance of the discovery-document byte ceiling, reported with every
     * over-limit rejection so a reader knows which knob produced the bound and that it is settable.
     */
    private static final String BOUND_ORIGIN =
            "ClientConfiguration.discoveryDocumentMaxSize; settable via ClientConfiguration.builder()";

    private final ClientConfiguration configuration;
    private final DslJson<Object> dslJson;
    private final int maxContentSize;
    private final BackChannelHttp backChannel;

    /**
     * Creates a resolver for the authorization server described by the given client configuration.
     *
     * @param configuration the client configuration carrying the issuer and TLS policy; must not
     *                      be {@code null}
     */
    public DiscoveryResolver(ClientConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        this.dslJson = ParserConfig.builder().build().getDslJson();
        // The discovery document is bounded by its own configured ceiling, not by the JWT payload
        // ceiling (ParserConfig.getMaxPayloadSize()): the two are unrelated artifacts with unrelated
        // size profiles, and an ordinary Keycloak realm already publishes a document larger than the
        // 8 KiB payload bound, which failed discovery outright with no deployment-side remedy.
        this.maxContentSize = configuration.getDiscoveryDocumentMaxSize();
        this.backChannel = new BackChannelHttp(configuration, maxContentSize, BOUND_ORIGIN);
    }

    /**
     * Fetches and parses the authorization server's discovery document.
     *
     * @return the resolved provider metadata
     * @throws TransportException if the issuer is non-TLS (and cleartext is not permitted), the
     *         discovery request fails, the response is not successful, carries no
     *         {@code application/json} {@code Content-Type} or is unparseable, the document's issuer
     *         does not match the configured issuer, or an {@code https} issuer's document advertises a
     *         non-{@code https} endpoint (regardless of {@link ClientConfiguration#allowInsecureHttp})
     */
    public ProviderMetadata resolve() {
        String issuer = configuration.getIssuer();
        enforceTls(issuer);

        String wellKnownUrl = toWellKnownUrl(issuer);
        String body = fetch(wellKnownUrl, issuer);
        ProviderMetadata metadata = parse(body, issuer);

        validateIssuer(metadata, issuer);
        validateEndpointSchemes(metadata, issuer);
        validateTokenEndpoint(metadata, issuer);
        recordCapabilities(metadata, issuer);

        LOGGER.info(ClientLogMessages.INFO.DISCOVERY_RESOLVED, issuer);
        return metadata;
    }

    private void enforceTls(String issuer) {
        URI issuerUri;
        try {
            issuerUri = URI.create(issuer);
        } catch (IllegalArgumentException e) {
            throw new TransportException(
                    ClientLogMessages.ERROR.DISCOVERY_FAILED.format(issuer, "malformed issuer URL"), e);
        }
        boolean https = SCHEME_HTTPS.equalsIgnoreCase(issuerUri.getScheme());
        if (!https && !configuration.isAllowInsecureHttp()) {
            LOGGER.warn(ClientLogMessages.WARN.NON_TLS_ISSUER, issuer);
            throw new TransportException(ClientLogMessages.WARN.NON_TLS_ISSUER.format(issuer));
        }
    }

    private String toWellKnownUrl(String issuer) {
        return stripTrailingSlash(issuer) + WELL_KNOWN_SUFFIX;
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String fetch(String wellKnownUrl, String issuer) {
        String failureContext = ClientLogMessages.ERROR.DISCOVERY_FAILED.format(issuer, "discovery request failed");
        HttpHandler handler = backChannel.validatedHandler(wellKnownUrl, failureContext);

        HttpClient client = backChannel.sharedClient(handler);
        HttpRequest request = handler.requestBuilder()
                .GET()
                .setHeader("Accept", "application/json")
                .build();
        try {
            HttpResponse<String> response = client.send(request, backChannel.bodyHandler());
            if (!HttpStatusFamily.isSuccess(response.statusCode())) {
                LOGGER.warn(ClientLogMessages.WARN.DISCOVERY_STATUS, issuer, response.statusCode());
                throw new TransportException(ClientLogMessages.ERROR.DISCOVERY_FAILED.format(
                        issuer, "unexpected HTTP status " + response.statusCode()));
            }
            if (!isJsonContentType(response)) {
                throw new TransportException(ClientLogMessages.ERROR.DISCOVERY_FAILED.format(
                        issuer, "discovery response Content-Type is not application/json"));
            }
            return response.body();
        } catch (IOException e) {
            LOGGER.error(e, ClientLogMessages.ERROR.DISCOVERY_FAILED, issuer, e.getMessage());
            throw new TransportException(ClientLogMessages.ERROR.DISCOVERY_FAILED.format(issuer, e.getMessage()), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransportException(ClientLogMessages.ERROR.DISCOVERY_FAILED.format(issuer, "request interrupted"), e);
        }
    }

    /**
     * Parses the discovery document body.
     *
     * @param body   the response body; may be {@code null}, since {@link HttpResponse#body()} crosses
     *               the HTTP trust boundary and carries no nullness guarantee — a {@code null} or blank
     *               body fails closed
     * @param issuer the configured issuer, for error context
     * @return the parsed provider metadata
     * @throws TransportException if the body is empty, oversized or unparseable
     */
    private ProviderMetadata parse(@Nullable String body, String issuer) {
        if (body == null || body.isBlank()) {
            throw new TransportException(ClientLogMessages.ERROR.DISCOVERY_FAILED.format(issuer, "empty discovery response"));
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxContentSize) {
            throw new TransportException(ClientLogMessages.ERROR.DISCOVERY_FAILED.format(
                    issuer, "discovery response exceeds maximum allowed size of " + maxContentSize
                    + " bytes (" + BOUND_ORIGIN + ")"));
        }
        try {
            ProviderMetadata metadata = dslJson.deserialize(ProviderMetadata.class, bytes, bytes.length);
            if (metadata == null) {
                throw new TransportException(ClientLogMessages.ERROR.DISCOVERY_FAILED.format(
                        issuer, "discovery document could not be parsed"));
            }
            return metadata;
        } catch (IOException e) {
            LOGGER.error(e, ClientLogMessages.ERROR.DISCOVERY_FAILED, issuer, e.getMessage());
            throw new TransportException(ClientLogMessages.ERROR.DISCOVERY_FAILED.format(issuer, e.getMessage()), e);
        }
    }

    private void validateIssuer(ProviderMetadata metadata, String issuer) {
        // OpenID Connect Discovery §4.3 requires the document issuer to equal the issuer used as the
        // .well-known URL prefix, which strips a trailing slash. Normalize both sides symmetrically so
        // a spec-compliant issuer configured with a trailing slash (e.g. .../realms/x/) still matches
        // the AS-reported .../realms/x (M10), rather than failing every discovery.
        String documentIssuer = metadata.issuer;
        if (documentIssuer == null || !stripTrailingSlash(documentIssuer).equals(stripTrailingSlash(issuer))) {
            // documentIssuer is taken from the AS discovery response body (OP-controlled, not
            // fully trusted); sanitize before it reaches either the log or the exception message so it
            // cannot forge a log entry (CWE-117) via a plain-text appender on the exception path.
            String sanitized = LogSanitizer.sanitize(documentIssuer);
            LOGGER.warn(ClientLogMessages.WARN.ISSUER_MISMATCH, sanitized, issuer);
            throw new TransportException(ClientLogMessages.WARN.ISSUER_MISMATCH.format(sanitized, issuer));
        }
    }

    /**
     * Refuses a scheme downgrade inside an HTTPS discovery document: when the configured issuer is
     * {@code https}, every advertised endpoint that is present must be {@code https} as well. The check
     * is not relaxed by {@link ClientConfiguration#allowInsecureHttp}, which exists for an {@code http}
     * issuer in a local setup, not for cleartext endpoints advertised by a TLS issuer. The refusal names
     * the endpoint label only; the advertised URL is untrusted and is not echoed.
     */
    private static void validateEndpointSchemes(ProviderMetadata metadata, String issuer) {
        if (!hasHttpsScheme(issuer)) {
            return;
        }
        Map<String, String> endpoints = new LinkedHashMap<>();
        endpoints.put("authorization_endpoint", metadata.authorizationEndpoint);
        endpoints.put("token_endpoint", metadata.tokenEndpoint);
        endpoints.put("userinfo_endpoint", metadata.userinfoEndpoint);
        endpoints.put("jwks_uri", metadata.jwksUri);
        endpoints.put("end_session_endpoint", metadata.endSessionEndpoint);
        endpoints.put("revocation_endpoint", metadata.revocationEndpoint);
        endpoints.put("introspection_endpoint", metadata.introspectionEndpoint);
        endpoints.put("pushed_authorization_request_endpoint", metadata.pushedAuthorizationRequestEndpoint);
        endpoints.forEach((label, value) -> {
            if (value != null && !hasHttpsScheme(value)) {
                throw new TransportException(ClientLogMessages.ERROR.DISCOVERY_FAILED.format(
                        issuer, "https issuer advertises a non-https " + label));
            }
        });
    }

    private static boolean hasHttpsScheme(String url) {
        return url.regionMatches(true, 0, HTTPS_PREFIX, 0, HTTPS_PREFIX.length());
    }

    /**
     * Whether the response declares the JSON media type: the {@code Content-Type} header is present
     * and its media type, ignoring parameters such as {@code charset}, is {@code application/json}
     * (case-insensitive).
     */
    private static boolean isJsonContentType(HttpResponse<String> response) {
        return response.headers().firstValue(CONTENT_TYPE)
                .map(value -> value.split(";", 2)[0].trim())
                .filter(JSON_MEDIA_TYPE::equalsIgnoreCase)
                .isPresent();
    }

    private void validateTokenEndpoint(ProviderMetadata metadata, String issuer) {
        if (metadata.getTokenEndpoint().isEmpty()) {
            throw new TransportException(ClientLogMessages.ERROR.DISCOVERY_FAILED.format(
                    issuer, "discovery document is missing the token_endpoint"));
        }
    }

    private void recordCapabilities(ProviderMetadata metadata, String issuer) {
        if (!metadata.supportsS256()) {
            LOGGER.warn(ClientLogMessages.WARN.S256_NOT_ADVERTISED, issuer);
        }
    }
}
