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
package de.cuioss.sheriff.token.integration.client;

import de.cuioss.sheriff.token.client.auth.ClientAuthentication;
import de.cuioss.sheriff.token.client.auth.ClientSecretBasicAuth;
import de.cuioss.sheriff.token.client.config.ClientAuthMethod;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.client.discovery.DiscoveryResolver;
import de.cuioss.sheriff.token.client.discovery.ProviderMetadata;
import de.cuioss.sheriff.token.client.dpop.DpopProofGenerator;
import de.cuioss.sheriff.token.client.dpop.SenderConstraint;
import de.cuioss.sheriff.token.client.flow.RefreshFlow;
import de.cuioss.sheriff.token.client.flow.TokenEndpointClient;
import de.cuioss.sheriff.token.client.token.IdTokenValidationBridge;
import de.cuioss.sheriff.token.client.token.TokenValidationBridge;
import de.cuioss.sheriff.token.commons.transport.HttpJwksLoaderConfig;
import de.cuioss.sheriff.token.validation.IssuerConfig;
import de.cuioss.sheriff.token.validation.TokenValidator;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Assembles the <em>production</em> client engine against the running Keycloak container, so an
 * integration test can drive {@link RefreshFlow} and
 * {@link de.cuioss.sheriff.token.client.lifecycle.TokenLifecycleManager} through exactly the wiring an
 * application performs — no hand-rolled {@code HttpRequest} to the token endpoint.
 *
 * <h2>Endpoints come from discovery, not from constants</h2>
 * The engine addresses the {@code client-engine} realm, which is imported with a {@code frontendUrl}
 * of {@link KeycloakUrlSupport#EXTERNAL_BASE} — the very authority the test JVM reaches Keycloak on.
 * Issuer identity and endpoint authority therefore coincide, which is what lets the support run a real
 * discovery round trip: {@link #discoveredProviderMetadata()} fetches the realm's
 * {@code .well-known/openid-configuration} through the production {@link DiscoveryResolver} and every
 * endpoint the flows use (token, revocation, JWKS) is the one the authorization server itself
 * advertises. No endpoint is assembled by hand, so a realm that moved an endpoint breaks the specs
 * instead of being silently papered over by a stale constant.
 * <p>
 * The {@code integration} realm is unsuitable for this: it advertises the Docker-internal
 * {@link KeycloakUrlSupport#INTERNAL_BASE} authority, which the test JVM cannot connect to, so a
 * discovery round trip against it would resolve endpoints that are unreachable from here.
 *
 * <h2>TLS posture</h2>
 * The container serves a self-signed certificate. Rather than disabling server authentication with a
 * trust-all {@code TrustManager}, this support loads the generated
 * {@code localhost-truststore.p12} and performs full chain validation against it — the trust is
 * narrowed to the known test CA, not switched off.
 */
final class RefreshEngineSupport {

    /** Name of the realm whose {@code frontendUrl} is the externally reachable base. */
    static final String REALM = "client-engine";

    /** Realm path shared by every endpoint this support addresses. */
    static final String REALM_PATH = "/realms/" + REALM;

    /**
     * Issuer the {@code client-engine} realm advertises. Because the realm's {@code frontendUrl} is the
     * externally reachable base, this is simultaneously the {@code iss} claim of every token it signs
     * and the prefix of the {@code .well-known} document discovery fetches.
     */
    static final String ISSUER = KeycloakUrlSupport.EXTERNAL_BASE + REALM_PATH;

    /** Client the discovery round trip is configured under; discovery itself is unauthenticated. */
    private static final String DISCOVERY_CLIENT_ID = "integration-client";

    /** Shared secret of {@link #DISCOVERY_CLIENT_ID}. */
    private static final String DISCOVERY_CLIENT_SECRET = "integration-secret";

    /**
     * Realm that enforces single-use refresh tokens ({@code revokeRefreshToken=true},
     * {@code refreshTokenMaxReuse=0}). Realm-scoped in Keycloak, hence a realm of its own rather than a
     * client in {@link #REALM}.
     */
    static final String SINGLE_USE_REALM = "single-use-refresh";

    /** Issuer the {@code single-use-refresh} realm advertises; also its {@code .well-known} prefix. */
    static final String SINGLE_USE_ISSUER =
            KeycloakUrlSupport.EXTERNAL_BASE + "/realms/" + SINGLE_USE_REALM;

    /** The single confidential client of {@link #SINGLE_USE_REALM}. */
    static final String SINGLE_USE_CLIENT_ID = "single-use-client";

    /** Shared secret of {@link #SINGLE_USE_CLIENT_ID}. */
    static final String SINGLE_USE_CLIENT_SECRET = "single-use-secret";

    /** DPoP proof signing algorithm the container advertises and the tests use. */
    static final String DPOP_SIGNING_ALGORITHM = "RS256";

    /** Package prefix identifying a frame inside the production client engine. */
    static final String PRODUCTION_PACKAGE = "de.cuioss.sheriff.token.client.";

    /**
     * Truststore generated by {@code src/main/docker/certificates/generate-truststore.sh}, holding the
     * container's self-signed {@code localhost} certificate. Resolved relative to the module directory,
     * which is the working directory of a Maven-launched test.
     */
    private static final Path TRUSTSTORE_PATH =
            Path.of("src", "main", "docker", "certificates", "localhost-truststore.p12");

    /** Truststore password, fixed by {@code generate-truststore.sh}. */
    private static final char[] TRUSTSTORE_PASSWORD = "localhost-trust".toCharArray();

    private RefreshEngineSupport() {
        // utility class
    }

    /**
     * Builds an {@link SSLContext} that performs full certificate-chain validation against the
     * generated test truststore.
     *
     * @return a chain-validating SSL context trusting only the container's test certificate
     * @throws IllegalStateException if the truststore is missing or cannot be read — the container
     *         fixtures were not generated, which is a setup failure, never a reason to fall back to
     *         an unvalidated context
     */
    static SSLContext chainValidatingSslContext() {
        if (!Files.isReadable(TRUSTSTORE_PATH)) {
            throw new IllegalStateException("Truststore not readable at " + TRUSTSTORE_PATH.toAbsolutePath()
                    + ". Run src/main/docker/certificates/generate-truststore.sh before the integration tests.");
        }
        try (InputStream truststore = Files.newInputStream(TRUSTSTORE_PATH)) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(truststore, TRUSTSTORE_PASSWORD);
            TrustManagerFactory trustManagerFactory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);
            SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());
            return sslContext;
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Failed to build a chain-validating SSLContext from "
                    + TRUSTSTORE_PATH.toAbsolutePath(), e);
        }
    }

    /**
     * @param clientId     the realm client to authenticate as
     * @param clientSecret the client's shared secret
     * @return a {@code client_secret_basic} configuration bound to the {@code client-engine} realm's
     *         issuer identity and carrying the chain-validating trust material
     */
    static ClientConfiguration clientConfiguration(String clientId, String clientSecret) {
        return clientConfiguration(clientId, clientSecret, ClientAuthMethod.CLIENT_SECRET_BASIC);
    }

    /**
     * @param clientId     the realm client to authenticate as
     * @param clientSecret the client's shared secret, or {@code null} for {@code private_key_jwt} —
     *                     and for the alpha {@code tls_client_auth} — where no shared secret exists
     * @param authMethod   the client-authentication method the configuration declares
     * @return a configuration bound to the {@code client-engine} realm's issuer identity and carrying
     *         the chain-validating trust material
     */
    static ClientConfiguration clientConfiguration(String clientId, @Nullable String clientSecret,
            ClientAuthMethod authMethod) {
        return clientConfiguration(ISSUER, clientId, clientSecret, authMethod);
    }

    /**
     * @param clientId     the client of the {@code single-use-refresh} realm
     * @param clientSecret the client's shared secret
     * @return a {@code client_secret_basic} configuration bound to the single-use realm's issuer
     */
    static ClientConfiguration singleUseClientConfiguration(String clientId, String clientSecret) {
        return clientConfiguration(SINGLE_USE_ISSUER, clientId, clientSecret,
                ClientAuthMethod.CLIENT_SECRET_BASIC);
    }

    /**
     * The one place a {@link ClientConfiguration} is built, for every realm this support addresses.
     *
     * @param issuer       the realm's issuer identity, which is also the discovery prefix
     * @param clientId     the realm client to authenticate as
     * @param clientSecret the client's shared secret, or {@code null} for the key-based methods
     * @param authMethod   the client-authentication method the configuration declares
     * @return the configuration, carrying the chain-validating trust material
     */
    private static ClientConfiguration clientConfiguration(String issuer, String clientId,
            @Nullable String clientSecret, ClientAuthMethod authMethod) {
        return ClientConfiguration.builder()
                .issuer(issuer)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .authMethod(authMethod)
                .scope("openid")
                .scope("profile")
                .scope("email")
                .sslContext(chainValidatingSslContext())
                .build();
    }

    /**
     * Provider metadata as the {@code client-engine} realm actually advertises it, fetched through the
     * production {@link DiscoveryResolver}. Every endpoint the flows address therefore comes from the
     * authorization server rather than from a constant maintained alongside it.
     * <p>
     * The document is fetched once per JVM per realm (see {@link #DISCOVERED}) — a discovery round trip
     * per call would add a network hop to every leg of every spec without changing the answer. A
     * {@code TransportException} from the resolver is allowed to propagate: a realm this fixture cannot
     * discover is a setup failure that must fail the run, never something to fall back from.
     * <p>
     * Each call returns a <em>fresh copy</em>. {@link ProviderMetadata} is mutable and specs legitimately
     * adjust a field on the instance they were handed (a deliberately failing revocation endpoint, a
     * pinned {@code token_endpoint_auth_methods_supported}); handing out the shared cached document would
     * let one such adjustment leak into every later test in the JVM.
     *
     * @return a private copy of the {@code client-engine} realm's discovered metadata
     */
    static ProviderMetadata discoveredProviderMetadata() {
        return discoveredProviderMetadata(ISSUER, DISCOVERY_CLIENT_ID, DISCOVERY_CLIENT_SECRET);
    }

    /**
     * @return a private copy of the {@code single-use-refresh} realm's discovered metadata, resolved
     *         through the same mechanism as {@link #discoveredProviderMetadata()}
     */
    static ProviderMetadata singleUseProviderMetadata() {
        return discoveredProviderMetadata(SINGLE_USE_ISSUER, SINGLE_USE_CLIENT_ID,
                SINGLE_USE_CLIENT_SECRET);
    }

    /**
     * Discovery documents already resolved, keyed by issuer — one entry per realm this support
     * addresses. A failed resolution stores nothing, so a later call retries rather than serving a
     * cached failure.
     */
    private static final Map<String, ProviderMetadata> DISCOVERED = new ConcurrentHashMap<>();

    /**
     * The one discovery mechanism, shared by every realm.
     *
     * @param issuer       the realm's issuer, which is both the cache key and the discovery prefix
     * @param clientId     the client the round trip is configured under
     * @param clientSecret that client's shared secret
     * @return a private copy of the realm's discovered metadata
     */
    private static ProviderMetadata discoveredProviderMetadata(String issuer, String clientId,
            String clientSecret) {
        return copyOf(DISCOVERED.computeIfAbsent(issuer, key -> new DiscoveryResolver(
                clientConfiguration(key, clientId, clientSecret, ClientAuthMethod.CLIENT_SECRET_BASIC))
                .resolve()));
    }

    /**
     * @param source the discovered document
     * @return a field-by-field copy, so a caller's mutation cannot reach the cached document
     */
    private static ProviderMetadata copyOf(ProviderMetadata source) {
        var copy = new ProviderMetadata();
        copy.issuer = source.issuer;
        copy.authorizationEndpoint = source.authorizationEndpoint;
        copy.tokenEndpoint = source.tokenEndpoint;
        copy.userinfoEndpoint = source.userinfoEndpoint;
        copy.jwksUri = source.jwksUri;
        copy.endSessionEndpoint = source.endSessionEndpoint;
        copy.revocationEndpoint = source.revocationEndpoint;
        copy.introspectionEndpoint = source.introspectionEndpoint;
        copy.pushedAuthorizationRequestEndpoint = source.pushedAuthorizationRequestEndpoint;
        copy.codeChallengeMethodsSupported = source.codeChallengeMethodsSupported;
        copy.tokenEndpointAuthMethodsSupported = source.tokenEndpointAuthMethodsSupported;
        copy.dpopSigningAlgValuesSupported = source.dpopSigningAlgValuesSupported;
        copy.authorizationResponseIssParameterSupported = source.authorizationResponseIssParameterSupported;
        return copy;
    }

    /**
     * Builds a real multi-issuer {@link TokenValidator} for the {@code client-engine} realm, over the
     * JWKS endpoint the realm's own discovery document advertises.
     * <p>
     * {@code audienceValidationDisabled(true)} because the realm's direct-access grant issues tokens
     * whose audience is the resource server, not the acquiring client; {@code claimSubOptional(false)}
     * keeps the RFC 7519 subject requirement in force, so the validated token is genuinely subject
     * bound. {@code allowLoopbackEgress(true)} is required because the JWKS endpoint is reached over
     * the host loopback port mapping, which the default egress policy rejects.
     *
     * @return a validator that fetches the realm's JWKS over the chain-validated loopback endpoint
     */
    static TokenValidator tokenValidator() {
        return tokenValidator(ISSUER, discoveredProviderMetadata().jwksUri);
    }

    /**
     * @return a validator for the {@code single-use-refresh} realm, over that realm's own
     *         discovery-advertised JWKS endpoint
     */
    static TokenValidator singleUseTokenValidator() {
        return tokenValidator(SINGLE_USE_ISSUER, singleUseProviderMetadata().jwksUri);
    }

    /**
     * The one validator assembly, shared by every realm.
     *
     * @param issuer  the issuer identity tokens of this realm must carry
     * @param jwksUri the realm's discovery-advertised JWKS endpoint
     * @return a validator that fetches that JWKS over the chain-validated loopback endpoint
     */
    private static TokenValidator tokenValidator(String issuer, String jwksUri) {
        HttpJwksLoaderConfig jwksConfig = HttpJwksLoaderConfig.builder()
                .jwksUrl(jwksUri)
                .issuerIdentifier(issuer)
                .sslContext(chainValidatingSslContext())
                .allowLoopbackEgress(true)
                .build();
        IssuerConfig issuerConfig = IssuerConfig.builder()
                .issuerIdentifier(issuer)
                .audienceValidationDisabled(true)
                .claimSubOptional(false)
                .httpJwksLoaderConfig(jwksConfig)
                .build();
        return TokenValidator.builder().issuerConfig(issuerConfig).build();
    }

    /**
     * @param validator the shared validator
     * @return the access-token validation bridge the flows validate through
     */
    static TokenValidationBridge accessTokenBridge(TokenValidator validator) {
        return new TokenValidationBridge(validator);
    }

    /**
     * @param validator the shared validator
     * @return the ID-token validation bridge the lifecycle wiring validates refreshed ID tokens through
     */
    static IdTokenValidationBridge idTokenBridge(TokenValidator validator) {
        return new IdTokenValidationBridge(validator);
    }

    /**
     * Assembles an unconstrained refresh flow exactly as an application would.
     *
     * @param configuration the client configuration
     * @param accessBridge  the access-token validation bridge
     * @return the wired refresh flow
     */
    static RefreshFlow refreshFlow(ClientConfiguration configuration, TokenValidationBridge accessBridge) {
        return refreshFlow(configuration, accessBridge, clientAuthentication(configuration));
    }

    /**
     * Assembles an unconstrained refresh flow over a caller-chosen client-authentication strategy, so
     * the refresh leg can be driven through any of the engine's {@code ClientAuthentication}
     * implementations rather than only the shared-secret Basic form.
     *
     * @param configuration        the client configuration
     * @param accessBridge         the access-token validation bridge
     * @param clientAuthentication the strategy to present at the token endpoint
     * @return the wired refresh flow
     */
    static RefreshFlow refreshFlow(ClientConfiguration configuration, TokenValidationBridge accessBridge,
            ClientAuthentication clientAuthentication) {
        return new RefreshFlow(configuration, new TokenEndpointClient(configuration), accessBridge,
                clientAuthentication);
    }

    /**
     * Assembles a DPoP-constrained refresh flow over a caller-owned key pair, so the acquisition leg
     * and the refresh leg can present proofs from the <em>same</em> key and the sender-constraint
     * continuity is observable in the rotated token's {@code cnf.jkt}.
     *
     * @param configuration the client configuration
     * @param accessBridge  the access-token validation bridge
     * @param keyPair       the proof key shared with the acquisition leg
     * @return the wired, DPoP-constrained refresh flow
     */
    static RefreshFlow dpopRefreshFlow(ClientConfiguration configuration, TokenValidationBridge accessBridge,
            KeyPair keyPair) {
        SenderConstraint constraint = SenderConstraint.dpop(dpopProofGenerator(keyPair));
        return new RefreshFlow(configuration, new TokenEndpointClient(configuration), accessBridge,
                clientAuthentication(configuration), constraint);
    }

    /**
     * @param keyPair the proof key
     * @return the production DPoP proof generator over {@code keyPair}
     */
    static DpopProofGenerator dpopProofGenerator(KeyPair keyPair) {
        return new DpopProofGenerator(keyPair, DPOP_SIGNING_ALGORITHM);
    }

    /**
     * @param configuration the client configuration
     * @return the shared-secret client authentication for {@code configuration}
     */
    static ClientSecretBasicAuth clientAuthentication(ClientConfiguration configuration) {
        return new ClientSecretBasicAuth(configuration.getClientId(), configuration.getClientSecret());
    }

    /**
     * @param failure the captured engine failure
     * @return the first {@code de.cuioss.sheriff.token.client.*} frame on the failure's cause chain, or
     *         {@link Optional#empty()} when no production frame is present
     */
    static Optional<String> productionFrame(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            for (StackTraceElement element : current.getStackTrace()) {
                if (element.getClassName().startsWith(PRODUCTION_PACKAGE)) {
                    return Optional.of(element.toString());
                }
            }
        }
        return Optional.empty();
    }

    /**
     * @return a freshly generated 2048-bit RSA key pair, for tests that own a proof or assertion
     *         signing key rather than sharing the container's realm-issued key material
     * @throws IllegalStateException if RSA is unavailable — never in practice, it is a mandatory JDK
     *         algorithm
     */
    static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA not available", e);
        }
    }
}
