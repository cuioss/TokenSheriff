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
package de.cuioss.sheriff.token.client.internal;

import de.cuioss.http.client.handler.HttpHandler;
import de.cuioss.sheriff.token.client.config.ClientAuthMethod;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.commons.error.TransportException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.net.ssl.SSLContext;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit coverage for the back-channel origin mapping rule at the
 * {@link BackChannelHttp#validatedHandler(String, String)} seam.
 * <p>
 * Every assertion reads the URI of the handler the seam builds, i.e. the URL that would actually be
 * contacted; no socket is opened. The routing of the five endpoint clients through a live server lives
 * in {@code BackChannelOriginRoutingTest}.
 */
@DisplayName("BackChannelHttp back-channel origin mapping")
class BackChannelOriginMappingTest {

    private static final int MAX_CONTENT_SIZE = 8192;
    private static final String ISSUER = "https://idp.example.com/realms/demo";
    private static final String TOKEN_PATH = "/realms/demo/protocol/openid-connect/token";
    private static final String TOKEN_ENDPOINT = "https://idp.example.com" + TOKEN_PATH;
    private static final String CLEARTEXT_ORIGIN = "http://keycloak:8080";
    private static final String TLS_ORIGIN = "https://keycloak.internal:8443";
    private static final String FAILURE_CONTEXT = "token endpoint request failed";

    @Nested
    @DisplayName("Mapping rule")
    class MappingRule {

        @Test
        @DisplayName("Should send a same-origin endpoint to the back-channel origin, keeping path and query verbatim")
        void shouldRewriteSameOriginEndpoint() {
            var backChannel = backChannel(builder().backChannelOrigin(CLEARTEXT_ORIGIN).allowInsecureHttp(true));

            URI target = targetOf(backChannel, TOKEN_ENDPOINT + "?scope=openid%20profile&x=a%2Fb");

            assertAll("origin replaced, request target kept",
                    () -> assertEquals("http", target.getScheme()),
                    () -> assertEquals("keycloak", target.getHost()),
                    () -> assertEquals(8080, target.getPort()),
                    () -> assertEquals(TOKEN_PATH, target.getRawPath()),
                    () -> assertEquals("scope=openid%20profile&x=a%2Fb", target.getRawQuery(),
                            "the raw query must pass through without decoding or re-encoding"));
        }

        @ParameterizedTest(name = "issuer {0}")
        @ValueSource(strings = {"https://idp.example.com", "https://idp.example.com:443"})
        @DisplayName("Should treat an explicit default port as the same origin as an absent one")
        void shouldTreatDefaultPortAsSameOrigin(String issuer) {
            var backChannel = backChannel(builder().issuer(issuer).backChannelOrigin(TLS_ORIGIN));

            URI implicitPort = targetOf(backChannel, "https://idp.example.com/token");
            URI explicitPort = targetOf(backChannel, "https://idp.example.com:443/token");

            assertAll("https://idp and https://idp:443 are one origin",
                    () -> assertEquals(URI.create(TLS_ORIGIN + "/token"), implicitPort),
                    () -> assertEquals(URI.create(TLS_ORIGIN + "/token"), explicitPort));
        }

        @Test
        @DisplayName("Should treat port 80 as the default of an http issuer")
        void shouldTreatPort80AsHttpDefault() {
            var backChannel = backChannel(builder().issuer("http://idp.example.com")
                    .backChannelOrigin(CLEARTEXT_ORIGIN).allowInsecureHttp(true));

            URI target = targetOf(backChannel, "http://idp.example.com:80/token");

            assertEquals(URI.create(CLEARTEXT_ORIGIN + "/token"), target);
        }

        @Test
        @DisplayName("Should compare scheme and host case-insensitively")
        void shouldNormaliseSchemeAndHostCase() {
            var backChannel = backChannel(builder().backChannelOrigin(TLS_ORIGIN));

            URI target = targetOf(backChannel, "HTTPS://IDP.Example.COM" + TOKEN_PATH);

            assertEquals(URI.create(TLS_ORIGIN + TOKEN_PATH), target);
        }

        @ParameterizedTest(name = "keeps {0}")
        @ValueSource(strings = {
                "https://other.example.com/token",
                "https://idp.example.com:8443/token",
                "https://idp.example.com.attacker.test/token",
                "http://idp.example.com/token"
        })
        @DisplayName("Should contact a foreign-origin endpoint exactly where it is advertised")
        void shouldKeepForeignOriginEndpoint(String endpoint) {
            var backChannel = backChannel(builder().backChannelOrigin(TLS_ORIGIN).allowInsecureHttp(true));

            URI target = targetOf(backChannel, endpoint);

            assertEquals(URI.create(endpoint), target,
                    "only an endpoint sharing the issuer origin may be retargeted");
        }

        @Test
        @DisplayName("Should leave every endpoint unchanged when no back-channel origin is configured")
        void shouldLeaveEndpointsUnchangedWithoutOrigin() {
            var backChannel = backChannel(builder());

            assertAll("an unconfigured origin is today's behaviour",
                    () -> assertEquals(URI.create(TOKEN_ENDPOINT), targetOf(backChannel, TOKEN_ENDPOINT)),
                    () -> assertEquals(URI.create("https://other.example.com/token"),
                            targetOf(backChannel, "https://other.example.com/token")));
        }
    }

    @Nested
    @DisplayName("Transport policy on the rewritten target")
    class TransportPolicy {

        @Test
        @DisplayName("Should refuse an http back-channel origin for a TLS issuer unless allowInsecureHttp is set")
        void shouldRefuseCleartextTargetWithoutAllowInsecureHttp() {
            var backChannel = backChannel(builder().backChannelOrigin(CLEARTEXT_ORIGIN));

            assertThrows(TransportException.class,
                    () -> backChannel.validatedHandler(TOKEN_ENDPOINT, FAILURE_CONTEXT),
                    "allowInsecureHttp is evaluated against the host contacted, not the advertised https URL");
        }

        @Test
        @DisplayName("Should accept an http back-channel origin once allowInsecureHttp is set")
        void shouldAcceptCleartextTargetWithAllowInsecureHttp() {
            var backChannel = backChannel(builder().backChannelOrigin(CLEARTEXT_ORIGIN).allowInsecureHttp(true));

            HttpHandler handler = assertDoesNotThrow(
                    () -> backChannel.validatedHandler(TOKEN_ENDPOINT, FAILURE_CONTEXT));

            assertEquals("http", handler.getUri().getScheme());
        }

        @Test
        @DisplayName("Should skip the TLS knobs for a cleartext rewritten target of a TLS issuer")
        void shouldSkipTlsKnobsForCleartextTarget() {
            // cui-http refuses verifyHostname(false) on an http:// URI: only a target-based cleartext
            // check lets this configuration build at all.
            var backChannel = backChannel(builder().backChannelOrigin(CLEARTEXT_ORIGIN)
                    .allowInsecureHttp(true).verifyHostname(false));

            assertDoesNotThrow(() -> backChannel.validatedHandler(TOKEN_ENDPOINT, FAILURE_CONTEXT),
                    "the cleartext decision must follow the rewritten target, not the advertised https URL");
        }

        @Test
        @DisplayName("Should apply verifyHostname(false) to a rewritten TLS target")
        void shouldApplyVerifyHostnameToRewrittenTlsTarget() {
            var backChannel = backChannel(builder().backChannelOrigin(TLS_ORIGIN).verifyHostname(false));

            HttpHandler handler = backChannel.validatedHandler(TOKEN_ENDPOINT, FAILURE_CONTEXT);

            assertAll("hostname posture travels with the rewritten TLS target",
                    () -> assertEquals("keycloak.internal", handler.getUri().getHost()),
                    () -> assertFalse(handler.isVerifyHostname()));
        }

        @Test
        @DisplayName("Should apply the configured SSL context to a rewritten TLS target")
        void shouldApplySslContextToRewrittenTlsTarget() {
            var configured = freshSslContext();
            var backChannel = backChannel(builder().backChannelOrigin(TLS_ORIGIN).sslContext(configured));

            HttpHandler handler = backChannel.validatedHandler(TOKEN_ENDPOINT, FAILURE_CONTEXT);

            assertAll("per-client trust travels with the rewritten TLS target",
                    () -> assertEquals("keycloak.internal", handler.getUri().getHost()),
                    () -> assertSame(configured, handler.getSslContext()));
        }

        @Test
        @DisplayName("Should still reject a traversal path on an endpoint that would be rewritten")
        void shouldStillRejectTraversalOnRewrittenEndpoint() {
            var backChannel = backChannel(builder().backChannelOrigin(TLS_ORIGIN));

            assertThrows(TransportException.class,
                    () -> backChannel.validatedHandler("https://idp.example.com/realms/../../etc/passwd",
                            FAILURE_CONTEXT),
                    "egress validation runs on the advertised URL before the origin is replaced");
        }
    }

    private static URI targetOf(BackChannelHttp backChannel, String endpointUrl) {
        return backChannel.validatedHandler(endpointUrl, FAILURE_CONTEXT).getUri();
    }

    private static BackChannelHttp backChannel(ClientConfiguration.ClientConfigurationBuilder builder) {
        return new BackChannelHttp(builder.build(), MAX_CONTENT_SIZE, BackChannelHttp.FIXED_PARSER_CONFIG_ORIGIN);
    }

    private static ClientConfiguration.ClientConfigurationBuilder builder() {
        return ClientConfiguration.builder()
                .issuer(ISSUER)
                .clientId("test-client")
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC);
    }

    private static SSLContext freshSslContext() {
        return assertDoesNotThrow(() -> {
            var context = SSLContext.getInstance("TLSv1.3");
            context.init(null, null, null);
            return context;
        });
    }
}
