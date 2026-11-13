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
package de.cuioss.sheriff.token.client.flow;

import de.cuioss.sheriff.token.client.auth.ClientSecretBasicAuth;
import de.cuioss.sheriff.token.client.config.ClientAuthMethod;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.client.discovery.ProviderMetadata;
import de.cuioss.sheriff.token.client.token.RotationResult;
import de.cuioss.sheriff.token.client.token.RotationResult.ScopeDelta;
import de.cuioss.sheriff.token.client.token.TokenValidationBridge;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.sheriff.token.validation.test.TestTokenHolder;
import de.cuioss.sheriff.token.validation.test.dispatcher.TokenDispatcher;
import de.cuioss.sheriff.token.validation.test.generator.TestTokenGenerators;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import lombok.Getter;
import lombok.NonNull;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the per-call scope overload {@link RefreshFlow#refresh(ProviderMetadata, String, List)}: the
 * per-call scopes are what the request sends and what the granted scope is reconciled against, the
 * shared configuration is never touched, and the two-argument call keeps its form unchanged.
 */
@EnableTestLogger
@EnableGeneratorController
@EnableMockWebServer
@DisplayName("RefreshFlow per-call scope override")
class RefreshFlowScopeOverrideTest {

    /** The scopes the client is configured with. */
    private static final List<String> CONFIGURED_SCOPES = List.of("openid", "profile");

    /** The per-call scopes, deliberately different from {@link #CONFIGURED_SCOPES}. */
    private static final List<String> PER_CALL_SCOPES = List.of("openid", "email");

    /** The rendering of {@link #PER_CALL_SCOPES} the request and the WARN records carry. */
    private static final String PER_CALL_RENDERED = "openid email";

    private static final long FUTURE_TIMEOUT_SECONDS = 20;

    @Getter
    private final ArrivalGatedTokenDispatcher moduleDispatcher = new ArrivalGatedTokenDispatcher();

    private TestTokenHolder holder;
    private TokenValidationBridge bridge;

    @BeforeEach
    void setUp() {
        holder = TestTokenGenerators.accessTokens().next();
        TokenValidator validator = TokenValidator.builder().issuerConfig(holder.getIssuerConfig()).build();
        bridge = new TokenValidationBridge(validator);
        moduleDispatcher.reset();
    }

    private static ClientConfiguration config(boolean strictScopeReconciliation) {
        return ClientConfiguration.builder()
                .issuer("https://" + Generators.letterStrings(3, 10).next() + ".example.com")
                .clientId(Generators.letterStrings(5, 12).next())
                .clientSecret(Generators.letterStrings(8, 20).next())
                .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
                .allowInsecureHttp(true)
                .allowLoopbackEgress(true)
                .scopes(CONFIGURED_SCOPES)
                .strictScopeReconciliation(strictScopeReconciliation)
                .build();
    }

    private static ProviderMetadata metadata(URIBuilder uriBuilder) {
        var metadata = new ProviderMetadata();
        metadata.tokenEndpoint = uriBuilder.addPathSegments("oidc", "token").buildAsString();
        return metadata;
    }

    private RefreshFlow flow(ClientConfiguration config) {
        return new RefreshFlow(config, new TokenEndpointClient(config), bridge,
                new ClientSecretBasicAuth(config.getClientId(), config.getClientSecret()));
    }

    /**
     * An RFC 6749 §5.1 success body carrying a real signed access token and the given granted
     * {@code scope}.
     */
    private String grantedScopeResponse(String grantedScope) {
        return "{\"access_token\":\"" + holder.getRawToken()
                + "\",\"token_type\":\"Bearer\",\"expires_in\":300,\"refresh_token\":\""
                + Generators.letterStrings(20, 40).next() + "\",\"scope\":\"" + grantedScope + "\"}";
    }

    private static String presentedToken() {
        return Generators.letterStrings(20, 40).next();
    }

    /** Decodes a form body into its {@code name=value} pairs, sorted, so duplicates stay visible. */
    private static List<String> decodedPairs(RecordedRequest request) {
        String body = request.getBody() == null ? "" : request.getBody().utf8();
        List<String> pairs = new ArrayList<>();
        for (String pair : body.split("&")) {
            pairs.add(URLDecoder.decode(pair, StandardCharsets.UTF_8));
        }
        pairs.sort(null);
        return pairs;
    }

    private static Optional<String> scopeParameter(RecordedRequest request) {
        return decodedPairs(request).stream()
                .filter(pair -> pair.startsWith("scope="))
                .map(pair -> pair.substring("scope=".length()))
                .findFirst();
    }

    @Test
    @DisplayName("Should send the per-call scopes and not the configured scopes")
    void shouldSendPerCallScopes(URIBuilder uriBuilder, MockWebServer server) throws Exception {
        moduleDispatcher.respondWith(grantedScopeResponse(PER_CALL_RENDERED));

        flow(config(false)).refresh(metadata(uriBuilder), presentedToken(), PER_CALL_SCOPES);

        List<String> pairs = decodedPairs(server.takeRequest());
        assertAll("the request carries exactly the per-call scope parameter",
                () -> assertTrue(pairs.contains("scope=" + PER_CALL_RENDERED),
                        "the per-call scopes must be sent space-delimited, but the form was: " + pairs),
                () -> assertEquals(1, pairs.stream().filter(pair -> pair.startsWith("scope=")).count(),
                        "exactly one scope parameter must be sent, but the form was: " + pairs));
    }

    @Test
    @DisplayName("Should leave the shared configuration's scopes unchanged by a per-call override")
    void shouldNotMutateConfiguration(URIBuilder uriBuilder, MockWebServer server) throws Exception {
        moduleDispatcher.respondWith(grantedScopeResponse(PER_CALL_RENDERED));
        ClientConfiguration config = config(false);
        var flow = flow(config);
        var metadata = metadata(uriBuilder);

        flow.refresh(metadata, presentedToken(), PER_CALL_SCOPES);
        moduleDispatcher.respondWith(grantedScopeResponse(String.join(" ", CONFIGURED_SCOPES)));
        RotationResult followUp = flow.refresh(metadata, presentedToken());

        server.takeRequest();
        RecordedRequest followUpRequest = server.takeRequest();
        assertAll("the override applied to one call only",
                () -> assertEquals(CONFIGURED_SCOPES, config.getScopes(),
                        "the configuration must still hold the configured scopes"),
                () -> assertEquals(Optional.of(String.join(" ", CONFIGURED_SCOPES)), scopeParameter(followUpRequest),
                        "a following two-argument refresh must send the configured scopes"),
                () -> assertEquals(ScopeDelta.EQUAL, followUp.scopeDelta(),
                        "a following two-argument refresh must reconcile against the configured scopes"));
    }

    @ParameterizedTest(name = "granted ''{0}'' -> {1}")
    @CsvSource({
            "openid email,   EQUAL",
            "email,          NARROWED",
            "openid profile, BROADENED"
    })
    @DisplayName("Should reconcile the granted scope against the per-call scopes, not the configured ones")
    void shouldReconcileAgainstPerCallBaseline(String granted, ScopeDelta expected, URIBuilder uriBuilder) {
        moduleDispatcher.respondWith(grantedScopeResponse(granted));

        RotationResult result = flow(config(false)).refresh(metadata(uriBuilder), presentedToken(), PER_CALL_SCOPES);

        assertAll("reconciliation against the per-call baseline",
                () -> assertEquals(expected, result.scopeDelta(),
                        "each granted set is chosen so its outcome against the configured scopes differs"),
                () -> assertEquals(granted, result.grantedScope(), "the raw granted scope must be surfaced"));
    }

    @Test
    @DisplayName("Should name the per-call scopes as the requested scope in the reconciliation WARN")
    void shouldReportPerCallScopesInWarn(URIBuilder uriBuilder) {
        moduleDispatcher.respondWith(grantedScopeResponse("email"));

        flow(config(false)).refresh(metadata(uriBuilder), presentedToken(), PER_CALL_SCOPES);

        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                "granted a narrower scope than requested on refresh; granted 'email', requested '"
                        + PER_CALL_RENDERED + "'");
    }

    @Test
    @DisplayName("Should refuse under strict reconciliation a grant broadened against the per-call scopes")
    void shouldRefuseBroadenedAgainstPerCallWhenStrict(URIBuilder uriBuilder) {
        String granted = String.join(" ", CONFIGURED_SCOPES);
        moduleDispatcher.respondWith(grantedScopeResponse(granted));
        var flow = flow(config(true));
        var metadata = metadata(uriBuilder);
        String presented = presentedToken();

        RedeemedScopeRefusalException refused = assertThrows(RedeemedScopeRefusalException.class,
                () -> flow.refresh(metadata, presented, PER_CALL_SCOPES),
                "a grant equal to the configured scopes is still broader than the per-call scopes");

        assertAll("the refusal names the per-call baseline and keeps the redemption",
                () -> assertTrue(refused.getMessage().contains("granted '" + granted + "', requested '"
                                + PER_CALL_RENDERED + "'"),
                        "the refusal must name the per-call scopes as requested: " + refused.getMessage()),
                () -> assertEquals(RefreshFailureClassification.Kind.REDEEMED, RefreshFlow.classify(refused).kind(),
                        "the strict refusal is raised after the exchange and must stay session-ending"));
    }

    @Test
    @DisplayName("Should accept under strict reconciliation a grant broader than configured but equal to per-call")
    void shouldAcceptPerCallEqualGrantWhenStrict(URIBuilder uriBuilder) {
        List<String> perCall = List.of("openid", "profile", "email");
        moduleDispatcher.respondWith(grantedScopeResponse(String.join(" ", perCall)));

        RotationResult result = flow(config(true)).refresh(metadata(uriBuilder), presentedToken(), perCall);

        assertAll("strict posture judges the grant against the per-call scopes",
                () -> assertNotNull(result.accessToken(), "the grant must be accepted"),
                () -> assertEquals(ScopeDelta.EQUAL, result.scopeDelta(),
                        "a grant equal to the per-call scopes is EQUAL even though it broadens the configured ones"));
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, RefreshFlow.class);
    }

    @Test
    @DisplayName("Should refuse a null, blank, multi-token or empty override before any HTTP call")
    void shouldRefuseInvalidOverridesBeforeAnyRequest(URIBuilder uriBuilder) {
        var flow = flow(config(false));
        var metadata = metadata(uriBuilder);
        String presented = presentedToken();
        List<String> nullEntry = Arrays.asList("openid", null);
        List<String> blankEntry = List.of("openid", "  ");
        List<String> multiTokenEntry = List.of("openid email");
        List<String> empty = List.of();

        assertAll("invalid overrides",
                () -> assertThrows(NullPointerException.class, () -> flow.refresh(metadata, presented, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> flow.refresh(metadata, presented, nullEntry)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> flow.refresh(metadata, presented, blankEntry)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> flow.refresh(metadata, presented, multiTokenEntry)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> flow.refresh(metadata, presented, empty)));
        moduleDispatcher.assertCallsAnswered(0);
    }

    @Test
    @DisplayName("Should send and judge each concurrent refresh by its own per-call scopes")
    void shouldKeepConcurrentOverridesApart(URIBuilder uriBuilder, MockWebServer server) throws Exception {
        String granted = "openid profile";
        List<String> equalScopes = List.of("openid", "profile");
        List<String> widerScopes = List.of("openid", "profile", "email");
        moduleDispatcher.respondWith(grantedScopeResponse(granted));
        moduleDispatcher.holdUntilArrived(2);
        var flow = flow(config(false));
        var metadata = metadata(uriBuilder);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<RotationResult> equal = executor.submit(() -> flow.refresh(metadata, presentedToken(), equalScopes));
            Future<RotationResult> wider = executor.submit(() -> flow.refresh(metadata, presentedToken(), widerScopes));
            RotationResult equalResult = equal.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            RotationResult widerResult = wider.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            Set<Optional<String>> sentScopes = new HashSet<>(List.of(
                    scopeParameter(server.takeRequest()), scopeParameter(server.takeRequest())));
            assertAll("both refreshes were in flight together and kept their own scopes",
                    () -> assertFalse(moduleDispatcher.timedOut(),
                            "no arrival wait may have timed out: a timed-out wait means the requests did not overlap"),
                    () -> assertTrue(moduleDispatcher.allArrived(),
                            "both requests must have reached the server before either was answered"),
                    () -> assertEquals(Set.of(Optional.of("openid profile"), Optional.of("openid profile email")),
                            sentScopes, "each request must carry its own per-call scopes"),
                    () -> assertEquals(ScopeDelta.EQUAL, equalResult.scopeDelta(),
                            "the call requesting 'openid profile' must judge the grant EQUAL"),
                    () -> assertEquals(ScopeDelta.NARROWED, widerResult.scopeDelta(),
                            "the call requesting 'openid profile email' must judge the same grant NARROWED"));
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * The two-argument call is unchanged by the overload: the form carries exactly the grant type, the
     * presented token and the configured scopes, and client authentication stays in the header. The
     * pairs are compared sorted because the form is built from an unordered map whose iteration order
     * is not specified.
     */
    @Test
    @DisplayName("Should keep the two-argument refresh form unchanged (golden form)")
    void shouldKeepTwoArgumentFormUnchanged(URIBuilder uriBuilder, MockWebServer server) throws Exception {
        moduleDispatcher.respondWith(grantedScopeResponse(String.join(" ", CONFIGURED_SCOPES)));
        ClientConfiguration config = config(false);
        String presented = presentedToken();

        RotationResult result = flow(config).refresh(metadata(uriBuilder), presented);

        RecordedRequest request = server.takeRequest();
        String expectedAuthorization = "Basic " + Base64.getEncoder().encodeToString(
                (config.getClientId() + ":" + config.getClientSecret()).getBytes(StandardCharsets.UTF_8));
        List<String> expectedPairs = new ArrayList<>(List.of(
                "grant_type=refresh_token",
                "refresh_token=" + presented,
                "scope=openid profile"));
        expectedPairs.sort(null);
        assertAll("golden two-argument refresh request",
                () -> assertEquals(expectedPairs, decodedPairs(request), "the form must be exactly the golden form"),
                () -> assertEquals(expectedAuthorization, request.getHeaders().get("Authorization"),
                        "client authentication must stay in the Authorization header"),
                () -> assertEquals(ScopeDelta.EQUAL, result.scopeDelta(),
                        "the two-argument call reconciles against the configured scopes"));
    }

    /**
     * A token-endpoint dispatcher that can hold every request until a given number of requests has
     * arrived, so a test observes requests that are in flight at the same time.
     */
    static final class ArrivalGatedTokenDispatcher extends TokenDispatcher {

        private static final long ARRIVAL_TIMEOUT_SECONDS = 10;

        private volatile CountDownLatch arrivals;
        private volatile boolean allArrived;

        /**
         * Set once any request's arrival wait timed out or was interrupted; a later arrival never
         * clears it, so a request that proceeded alone stays visible.
         */
        private volatile boolean arrivalTimedOut;

        void holdUntilArrived(int parties) {
            this.arrivals = new CountDownLatch(parties);
            this.allArrived = false;
            this.arrivalTimedOut = false;
        }

        /** Reports whether every held request arrived and no arrival wait timed out. */
        boolean allArrived() {
            return allArrived && !arrivalTimedOut;
        }

        /** Reports whether any arrival wait timed out or was interrupted. */
        boolean timedOut() {
            return arrivalTimedOut;
        }

        @Override
        public void reset() {
            super.reset();
            this.arrivals = null;
            this.allArrived = false;
            this.arrivalTimedOut = false;
        }

        @Override
        public Optional<MockResponse> handlePost(@NonNull RecordedRequest request) {
            CountDownLatch gate = this.arrivals;
            if (gate != null) {
                gate.countDown();
                try {
                    if (gate.await(ARRIVAL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        allArrived = true;
                    } else {
                        arrivalTimedOut = true;
                    }
                } catch (InterruptedException e) {
                    arrivalTimedOut = true;
                    Thread.currentThread().interrupt();
                }
            }
            return super.handlePost(request);
        }
    }
}
