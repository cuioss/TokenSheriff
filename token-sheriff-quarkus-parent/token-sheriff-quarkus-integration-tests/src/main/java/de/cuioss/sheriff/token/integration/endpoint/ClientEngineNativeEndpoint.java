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
package de.cuioss.sheriff.token.integration.endpoint;

import de.cuioss.sheriff.token.client.auth.ClientSecretBasicAuth;
import de.cuioss.sheriff.token.client.config.ClientAuthMethod;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.client.dpop.DpopProofGenerator;
import de.cuioss.sheriff.token.client.dpop.SenderConstraint;
import de.cuioss.sheriff.token.client.flow.CredentialRejectedException;
import de.cuioss.sheriff.token.client.flow.RedeemedResponseException;
import de.cuioss.sheriff.token.client.flow.TokenEndpointClient;
import de.cuioss.sheriff.token.commons.error.TransportException;
import de.cuioss.tools.logging.CuiLogger;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.*;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Probe endpoint that drives the production client engine ({@link TokenEndpointClient}) from inside
 * the native executable, so the native integration test can observe the engine's refusals where the
 * native-image metadata shipped with {@code token-sheriff-client} actually matters.
 * <p>
 * Every code path lives under {@code src/main}, so the native image keeps the engine classes and the
 * reflection registration of the generated DslJson converters is exercised for real. The endpoint
 * carries its engine configuration in code; it needs no application-side native-image entry.
 * <p>
 * Three operations are exposed:
 * <ul>
 *   <li>{@code GET /client-native/credential-rejection} redeems a bogus refresh token at the Keycloak
 *       {@code integration} realm and reports how the engine classified the {@code invalid_grant}
 *       answer.</li>
 *   <li>{@code POST /client-native/stub-token} is an in-app token endpoint stub answering a
 *       {@code DPoP} token type with an unsigned JWT access token bound to the caller-supplied
 *       {@code cnf.jkt}.</li>
 *   <li>{@code GET /client-native/dpop-binding?mode=match|other} requests a DPoP-constrained token from
 *       the stub, bound either to the proof key ({@code match}) or to another key ({@code other}), and
 *       reports whether the engine accepted the binding.</li>
 * </ul>
 * TEST CONFIGURATION ONLY: the engine configuration allow-lists the Docker host {@code keycloak} and
 * opts into loopback egress for the in-app stub.
 *
 * @since 1.0
 */
@Path("/client-native")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RegisterForReflection
@RunOnVirtualThread
public class ClientEngineNativeEndpoint {

    private static final CuiLogger LOGGER = new CuiLogger(ClientEngineNativeEndpoint.class);

    /** Outcome: the engine accepted the token endpoint's answer. */
    static final String OUTCOME_ACCEPTED = "accepted";

    /** Outcome: the engine classified the refusal as a rejected credential. */
    static final String OUTCOME_CREDENTIAL_REJECTED = "credential_rejected";

    /** Outcome: the engine reported a transport failure other than a rejected credential. */
    static final String OUTCOME_TRANSPORT_FAILURE = "transport_failure";

    /** Outcome: the engine refused a token whose {@code cnf.jkt} names another key. */
    static final String OUTCOME_JKT_MISMATCH = "jkt_mismatch";

    /** Outcome: the engine refused the stub's answer for a reason other than the jkt mismatch. */
    static final String OUTCOME_OTHER_REFUSAL = "other_refusal";

    /** DPoP-binding mode: the stub binds the token to the proof key. */
    static final String MODE_MATCH = "match";

    /** DPoP-binding mode: the stub binds the token to another key. */
    static final String MODE_OTHER = "other";

    private static final String OUTCOME = "outcome";

    private static final String KEYCLOAK_HOST = "keycloak";
    private static final String INTEGRATION_ISSUER = "https://keycloak:8443/realms/integration";
    private static final String INTEGRATION_TOKEN_ENDPOINT = INTEGRATION_ISSUER + "/protocol/openid-connect/token";
    private static final String INTEGRATION_CLIENT_ID = "integration-client";
    private static final String INTEGRATION_CLIENT_SECRET = "integration-secret";
    private static final String BOGUS_REFRESH_TOKEN = "client-native-bogus-refresh-token";

    /** The stub is reached over the container's own HTTPS port, which serves the localhost certificate. */
    private static final String STUB_TOKEN_ENDPOINT = "https://localhost:8443/client-native/stub-token";

    /** Form parameter carrying the thumbprint the stub binds the issued token to. */
    private static final String PROBE_JKT_PARAMETER = "probe_jkt";

    private static final String DPOP_SIGNING_ALGORITHM = "RS256";

    /** Fixed text of the engine's {@code cnf.jkt} mismatch refusal (see {@code TokenEndpointClient}). */
    private static final String JKT_MISMATCH_MARKER = "does not match the DPoP proof-key thumbprint";

    /** A JWK thumbprint is base64url without padding; nothing else may enter the stub's JSON. */
    private static final Pattern BASE64URL = Pattern.compile("[A-Za-z0-9_-]+");

    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    /** Container trust material holding the self-signed localhost and keycloak certificates. */
    private static final java.nio.file.Path TRUSTSTORE_PATH =
            java.nio.file.Path.of("/app/certificates/localhost-truststore.p12");
    private static final char[] TRUSTSTORE_PASSWORD = "localhost-trust".toCharArray();

    /**
     * Redeems a bogus refresh token at the Keycloak {@code integration} realm, authenticated as the
     * confidential {@code integration-client}, and reports how the engine classified the answer.
     *
     * @return {@code credential_rejected} when the engine raised {@link CredentialRejectedException},
     *         {@code transport_failure} for any other {@link TransportException}, {@code accepted}
     *         otherwise
     */
    @GET
    @Path("/credential-rejection")
    public Response credentialRejection() {
        TokenEndpointClient client = new TokenEndpointClient(clientConfiguration());
        Map<String, String> form = new HashMap<>();
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", BOGUS_REFRESH_TOKEN);
        Map<String, String> headers = new HashMap<>();
        new ClientSecretBasicAuth(INTEGRATION_CLIENT_ID, INTEGRATION_CLIENT_SECRET).decorate(form, headers);
        try {
            client.requestToken(INTEGRATION_TOKEN_ENDPOINT, form, headers);
            return outcome(OUTCOME_ACCEPTED);
        } catch (CredentialRejectedException e) {
            LOGGER.debug("Credential-rejection probe: credential rejected: %s", e.getMessage());
            return outcome(OUTCOME_CREDENTIAL_REJECTED);
        } catch (TransportException e) {
            LOGGER.debug("Credential-rejection probe: transport failure: %s", e.getMessage());
            return outcome(OUTCOME_TRANSPORT_FAILURE);
        }
    }

    /**
     * In-app token endpoint stub: answers a {@code DPoP} token type with an unsigned compact JWT access
     * token whose payload carries {@code cnf.jkt} = the thumbprint passed in by the caller.
     *
     * @param jkt the thumbprint to bind the issued token to; must be base64url
     * @return the token response, or {@code 400} when the thumbprint is absent or not base64url
     */
    @POST
    @Path("/stub-token")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response stubToken(@FormParam(PROBE_JKT_PARAMETER) @Nullable String jkt) {
        if (jkt == null || !BASE64URL.matcher(jkt).matches()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "invalid_request"))
                    .build();
        }
        String header = encode("{\"alg\":\"none\",\"typ\":\"at+jwt\"}");
        String payload = encode("{\"iss\":\"client-native-stub\",\"cnf\":{\"jkt\":\"" + jkt + "\"}}");
        String accessToken = header + "." + payload + ".";
        return Response.ok(Map.of(
                "access_token", accessToken,
                "token_type", "DPoP",
                "expires_in", 300)).build();
    }

    /**
     * Requests a DPoP-constrained token from the in-app stub with a fresh proof key and reports whether
     * the engine accepted the issued binding.
     *
     * @param mode {@code match} to have the stub bind the token to the proof key, {@code other} to have
     *             it bind the token to another key's thumbprint
     * @return {@code accepted}, {@code jkt_mismatch} when the engine refused the {@code cnf.jkt}
     *         mismatch, {@code other_refusal} for any other refusal, or {@code 400} for an unknown mode
     */
    @GET
    @Path("/dpop-binding")
    public Response dpopBinding(@QueryParam("mode") @Nullable String mode) {
        if (!MODE_MATCH.equals(mode) && !MODE_OTHER.equals(mode)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "mode must be '" + MODE_MATCH + "' or '" + MODE_OTHER + "'"))
                    .build();
        }
        DpopProofGenerator generator = new DpopProofGenerator(generateRsaKeyPair(), DPOP_SIGNING_ALGORITHM);
        String boundJkt = MODE_MATCH.equals(mode)
                ? generator.jkt()
                : new DpopProofGenerator(generateRsaKeyPair(), DPOP_SIGNING_ALGORITHM).jkt();
        Map<String, String> form = new HashMap<>();
        form.put("grant_type", "client_credentials");
        form.put(PROBE_JKT_PARAMETER, boundJkt);
        TokenEndpointClient client = new TokenEndpointClient(clientConfiguration());
        try {
            client.requestToken(STUB_TOKEN_ENDPOINT, form, new HashMap<>(), SenderConstraint.dpop(generator));
            return outcome(OUTCOME_ACCEPTED);
        } catch (RedeemedResponseException e) {
            LOGGER.debug("DPoP-binding probe (%s): redeemed-response refusal: %s", mode, e.getMessage());
            String message = e.getMessage();
            return outcome(message != null && message.contains(JKT_MISMATCH_MARKER)
                    ? OUTCOME_JKT_MISMATCH
                    : OUTCOME_OTHER_REFUSAL);
        } catch (TransportException e) {
            LOGGER.debug("DPoP-binding probe (%s): refusal: %s", mode, e.getMessage());
            return outcome(OUTCOME_OTHER_REFUSAL);
        }
    }

    /**
     * Builds the engine configuration: the Keycloak {@code integration} realm as issuer, the Docker host
     * {@code keycloak} allow-listed, loopback egress opted into for the in-app stub, and the container
     * trust material as the per-client SSL context when it is present. TEST CONFIGURATION ONLY.
     */
    private static ClientConfiguration clientConfiguration() {
        return ClientConfiguration.builder()
                .issuer(INTEGRATION_ISSUER)
                .clientId(INTEGRATION_CLIENT_ID)
                .clientSecret(INTEGRATION_CLIENT_SECRET)
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .allowedEgressHost(KEYCLOAK_HOST)
                .allowLoopbackEgress(true)
                .sslContext(containerSslContext())
                .build();
    }

    /**
     * @return a chain-validating SSL context over the container trust store, or {@code null} to fall
     *         back to the JVM default trust store when the container trust store is absent
     * @throws IllegalStateException when the trust store is present but cannot be read
     */
    private static @Nullable SSLContext containerSslContext() {
        if (!Files.isReadable(TRUSTSTORE_PATH)) {
            LOGGER.debug("Container trust store %s not readable; using the JVM default trust store", TRUSTSTORE_PATH);
            return null;
        }
        try (InputStream truststore = Files.newInputStream(TRUSTSTORE_PATH)) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(truststore, TRUSTSTORE_PASSWORD);
            TrustManagerFactory trustManagerFactory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());
            return sslContext;
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Failed to build an SSLContext from " + TRUSTSTORE_PATH, e);
        }
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA not available", e);
        }
    }

    private static String encode(String json) {
        return BASE64_URL.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static Response outcome(String outcome) {
        return Response.ok(Map.of(OUTCOME, outcome)).build();
    }
}
