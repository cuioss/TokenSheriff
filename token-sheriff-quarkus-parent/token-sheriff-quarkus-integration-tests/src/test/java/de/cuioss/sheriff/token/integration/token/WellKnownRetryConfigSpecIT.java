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
package de.cuioss.sheriff.token.integration.token;

import de.cuioss.sheriff.token.integration.BaseIntegrationTest;
import de.cuioss.tools.logging.CuiLogger;
import io.restassured.path.json.exception.JsonPathException;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves in a running native application that the configured retry settings reach well-known
 * discovery.
 * <p>
 * The spec does not talk to the application under test. It reads the <em>retry probe</em>: a second
 * container of the same native image (compose service {@code token-sheriff-retry-probe}) whose
 * {@code keycloak} issuer points at a port on which nothing listens, and whose retry maximum is set to
 * a value other than the default of five. Discovery therefore fails on every attempt, and the number
 * of attempts the probe logs is the number the retry configuration allowed.
 * <p>
 * An attempt is counted from the log lines of the HTTP retry adapter, which carry no URL. That is
 * sufficient here because discovery of the {@code keycloak} issuer is the only request of the probe
 * that fails: its second issuer, {@code integration}, loads its key set from the running Keycloak, and
 * the spec asserts that it did.
 * <p>
 * Every request names the probe's port explicitly. The static RestAssured defaults stay the ones of
 * {@link BaseIntegrationTest}, because all integration-test classes share one JVM.
 */
@DisplayName("Configured retry settings reach well-known discovery")
class WellKnownRetryConfigSpecIT extends BaseIntegrationTest {

    private static final CuiLogger LOGGER = new CuiLogger(WellKnownRetryConfigSpecIT.class);

    /** Maximum number of attempts the retry configuration allows when nothing is configured. */
    private static final int DEFAULT_MAX_ATTEMPTS = 5;

    private static final int PROBE_PORT = Integer.getInteger("test.retry-probe.https.port", 10444);
    private static final int CONFIGURED_MAX_ATTEMPTS = Integer.getInteger("test.retry-probe.max-attempts", 3);
    private static final Path PROBE_LOG =
            Path.of(System.getProperty("test.retry-probe.log.file", "target/retry-probe/quarkus.log"));

    /** Issuer whose well-known URL the probe points at a closed port. */
    private static final String UNREACHABLE_ISSUER = "https://keycloak:8443/realms/benchmark";
    /** Issuer the probe leaves untouched; it loads its key set from the running Keycloak. */
    private static final String REACHABLE_ISSUER = "https://keycloak:8443/realms/integration";

    private static final String READINESS_PATH = "/q/health/ready";
    private static final String JWKS_CHECK_DATA = "checks.find { it.name == 'jwks-endpoints' }.data";
    private static final String STATUS_UP = "UP";
    private static final String STATUS_DOWN = "DOWN";

    /** {@code HTTP-112}: logged for every failed attempt that is followed by another one. */
    private static final String RETRY_LINE_ID = "HTTP-112";
    /** {@code HTTP-111}: logged once, for the attempt after which the adapter gives up. */
    private static final String GIVE_UP_LINE_ID = "HTTP-111";

    /**
     * Upper bound for the probe to finish loading. With the configured maximum and delay the retries
     * take well under a second; the bound only absorbs container and CI jitter.
     */
    private static final Duration LOADING_BUDGET = Duration.ofSeconds(60);
    private static final Duration LOG_BUDGET = Duration.ofSeconds(10);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(500);

    @Test
    @DisplayName("Should make exactly the configured number of discovery attempts")
    void shouldMakeTheConfiguredNumberOfDiscoveryAttempts() throws Exception {
        assertNotEquals(DEFAULT_MAX_ATTEMPTS, CONFIGURED_MAX_ATTEMPTS,
                "the probe must be configured with a maximum other than the default, "
                        + "otherwise a discovery that ignores the setting would pass");

        await("retry probe finishes loading with the unreachable issuer down")
                .atMost(LOADING_BUDGET)
                .pollInterval(POLL_INTERVAL)
                .ignoreExceptions()
                .untilAsserted(WellKnownRetryConfigSpecIT::assertProbeFinishedLoading);

        List<String> giveUpLines = await("give-up line in the probe log")
                .atMost(LOG_BUDGET)
                .pollInterval(POLL_INTERVAL)
                .ignoreExceptions()
                .until(() -> probeLogLinesContaining(GIVE_UP_LINE_ID), lines -> !lines.isEmpty());
        List<String> retryLines = probeLogLinesContaining(RETRY_LINE_ID);
        LOGGER.debug("Retry probe logged %s retry line(s) and %s give-up line(s)",
                retryLines.size(), giveUpLines.size());

        assertAll("discovery attempts logged by the retry probe",
                () -> assertEquals(CONFIGURED_MAX_ATTEMPTS, retryLines.size() + giveUpLines.size(),
                        "the number of discovery attempts must equal the configured maximum"),
                () -> assertEquals(1, giveUpLines.size(),
                        "discovery must give up exactly once, was: " + giveUpLines),
                () -> assertTrue(giveUpLines.getFirst().contains(
                                "GET request failed after " + CONFIGURED_MAX_ATTEMPTS + " attempts"),
                        "the give-up line must name the configured maximum, was: " + giveUpLines.getFirst()),
                () -> assertEquals(CONFIGURED_MAX_ATTEMPTS - 1, retryLines.size(),
                        "every attempt but the last must be followed by a retry, was: " + retryLines));
    }

    /**
     * Asserts the final state of the probe: loading is over, the issuer with the unreachable
     * well-known URL is down and the other issuer is up. The last part is what makes the log count
     * meaningful — a second failing request would add retry lines of its own.
     */
    private static void assertProbeFinishedLoading() {
        // No status-code expectation: a readiness answer that is DOWN arrives as HTTP 503, and its
        // body is exactly what is read here.
        ExtractableResponse<Response> response = given()
                .port(PROBE_PORT)
                .when()
                .get(READINESS_PATH)
                .then()
                .extract();
        // Carried by every failure message below, so a failing run shows what the probe answered.
        String answer = "HTTP %s with body: %s".formatted(response.statusCode(), response.body().asString());

        Map<String, Object> data;
        try {
            data = response.jsonPath().getMap(JWKS_CHECK_DATA);
        } catch (JsonPathException e) {
            throw new AssertionError("the probe's readiness response must be JSON, was " + answer, e);
        }

        assertNotNull(data, "the probe's readiness response must carry the jwks-endpoints check, was " + answer);
        assertAll("readiness of the retry probe, answered " + answer,
                () -> assertEquals("COMPLETE", data.get("loading"), "loading must have finished"),
                () -> assertEquals(STATUS_DOWN, issuerStatus(data, UNREACHABLE_ISSUER),
                        "the issuer with the unreachable well-known URL must be down"),
                () -> assertEquals(STATUS_UP, issuerStatus(data, REACHABLE_ISSUER),
                        "the issuer with the working JWKS URL must be up"));
    }

    /**
     * @param data   the data of the {@code jwks-endpoints} readiness check, keyed
     *               {@code issuer.N.url} and {@code issuer.N.status}
     * @param issuer the issuer identifier to look up
     * @return the status reported for the issuer, or a marker text when the issuer is not reported
     */
    private static String issuerStatus(Map<String, Object> data, String issuer) {
        return data.entrySet().stream()
                .filter(entry -> entry.getKey().endsWith(".url") && issuer.equals(entry.getValue()))
                .map(entry -> entry.getKey().substring(0, entry.getKey().length() - "url".length()) + "status")
                .map(statusKey -> String.valueOf(data.get(statusKey)))
                .findFirst()
                .orElse("issuer not reported");
    }

    private static List<String> probeLogLinesContaining(String identifier) throws IOException {
        return Files.readAllLines(PROBE_LOG).stream()
                .filter(line -> line.contains(identifier))
                .toList();
    }
}
