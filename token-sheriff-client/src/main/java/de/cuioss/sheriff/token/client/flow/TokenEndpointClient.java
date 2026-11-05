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

import com.dslplatform.json.DslJson;
import com.dslplatform.json.JsonReader;
import de.cuioss.http.client.handler.HttpHandler;
import de.cuioss.http.client.handler.HttpStatusFamily;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.client.dpop.ConstraintBinding;
import de.cuioss.sheriff.token.client.dpop.SenderConstraint;
import de.cuioss.sheriff.token.client.internal.AccessTokenConfirmation;
import de.cuioss.sheriff.token.client.internal.BackChannelHttp;
import de.cuioss.sheriff.token.client.internal.FormEncoder;
import de.cuioss.sheriff.token.client.internal.LogSanitizer;
import de.cuioss.sheriff.token.client.token.TokenErrorResponse;
import de.cuioss.sheriff.token.client.token.TokenResponse;
import de.cuioss.sheriff.token.commons.error.TransportException;
import de.cuioss.sheriff.token.commons.transport.ParserConfig;
import de.cuioss.tools.logging.CuiLogger;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thin transport wrapper for the OAuth 2.0 back-channel token endpoint.
 * <p>
 * The client composes the commons-blessed cui-http {@link HttpHandler} (design fork 1) to perform
 * the authenticated, form-encoded {@code POST} to the token endpoint over TLS, then normalizes the
 * JSON response into a {@link TokenResponse}. It adds no transport hardening of its own — TLS/SSRF
 * posture is inherited from {@code HttpHandler}. Client authentication is decorated onto the request
 * by the caller (headers and/or form parameters); this class is auth-agnostic transport only.
 * <p>
 * <strong>The pre-redemption guarantee holds by construction, not by a guard.</strong> A connection
 * failure, a DNS failure, an SSRF-blocked target or an interrupt throws out of
 * {@code BackChannelHttp.validatedHandler} or {@code send} before any {@link HttpResponse} exists, so
 * such a failure can never reach the status check below and can never be classified as a rejected
 * credential. Only a response the server actually produced is ever classified. Preserve that ordering:
 * a transient network fault reported as a dead credential is exactly the inversion the classification
 * exists to prevent.
 *
 * @since 1.0
 * @author Oliver Wolff
 */
public class TokenEndpointClient {

    private static final CuiLogger LOGGER = new CuiLogger(TokenEndpointClient.class);

    private static final String CONTENT_TYPE = "Content-Type";
    private static final String FORM_URLENCODED = "application/x-www-form-urlencoded";
    private static final String HTTP_POST = "POST";
    private static final String DPOP_NONCE_HEADER = "DPoP-Nonce";
    private static final String FAILURE_CONTEXT = "Token endpoint request failed";

    /** Marker for "no status line was received", outside every HTTP status family. */
    private static final int NO_STATUS = -1;

    /**
     * The single RFC 6749 §5.2 error code this client treats as the authorization server declaring the
     * presented credential dead. It is an allow-list of one: see {@link #credentialRejected}.
     */
    private static final String ERROR_INVALID_GRANT = "invalid_grant";

    private final DslJson<Object> dslJson;
    private final int maxContentSize;
    private final BackChannelHttp backChannel;

    /**
     * @param configuration the client configuration carrying the TLS policy; must not be {@code null}
     */
    public TokenEndpointClient(ClientConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        ParserConfig parserConfig = ParserConfig.builder().build();
        this.dslJson = parserConfig.getDslJson();
        this.maxContentSize = parserConfig.getMaxPayloadSize();
        this.backChannel = new BackChannelHttp(configuration, maxContentSize,
                BackChannelHttp.FIXED_PARSER_CONFIG_ORIGIN);
    }

    /**
     * Performs a form-encoded {@code POST} to the token endpoint with no sender-constraint and
     * normalizes the response.
     *
     * @param tokenEndpoint    the absolute token endpoint URL (from discovery); must be TLS unless
     *                         {@link ClientConfiguration#allowInsecureHttp} is set
     * @param formParameters   the form-encoded request body parameters (e.g. {@code grant_type})
     * @param requestHeaders   additional request headers (e.g. an {@code Authorization} header)
     * @return the normalized token response
     * @throws TransportException if the request fails or the response is not successful; the
     *         {@link CredentialRejectedException} subtype when the authorization server declared the
     *         presented credential invalid, and the {@link RedeemedResponseException} subtype when the
     *         response was successful but its body could not be parsed — see
     *         {@link #requestToken(String, Map, Map, SenderConstraint)}
     */
    public TokenResponse requestToken(String tokenEndpoint,
            Map<String, String> formParameters,
            Map<String, String> requestHeaders) {
        return requestToken(tokenEndpoint, formParameters, requestHeaders, null);
    }

    /**
     * Performs a form-encoded {@code POST} to the token endpoint, attaching a DPoP proof when a
     * sender-constraint is supplied, and normalizes the response.
     * <p>
     * When {@code senderConstraint} is a DPoP constraint, a proof is generated and added as the
     * {@code DPoP} header. The proof's {@code htu} names the target URI of the request (RFC 9449 §4.2):
     * the URL actually contacted, which is {@code tokenEndpoint} itself or, when
     * {@link ClientConfiguration#backChannelOrigin} applied, its back-channel rewrite. If the
     * authorization server rejects the first attempt with a {@code use_dpop_nonce} challenge — a
     * non-success status carrying a {@code DPoP-Nonce} response header (RFC 9449 §8) — the nonce is
     * echoed into a fresh proof and the request is retried once, rather than surfacing the recoverable
     * challenge as an opaque transport failure. mTLS constraints add no header (the binding is
     * transport-level) and never trigger a nonce retry.
     *
     * @param tokenEndpoint    the absolute token endpoint URL (from discovery); must be TLS unless
     *                         {@link ClientConfiguration#allowInsecureHttp} is set
     * @param formParameters   the form-encoded request body parameters (e.g. {@code grant_type})
     * @param requestHeaders   additional request headers (e.g. an {@code Authorization} header)
     * @param senderConstraint the DPoP/mTLS sender-constraint to apply, or {@code null} for a plain
     *                         bearer-token request
     * @return the normalized token response
     * @throws TransportException if the request fails or the response is not successful (after a
     *         nonce retry where applicable) — in both cases, except for the
     *         {@link RedeemedResponseException} subtype described below, the authorization server
     *         never consumed the presented grant. Two narrower subtypes carry the distinctions a caller may need to
     *         fail closed on: {@link CredentialRejectedException} when the non-success status is a
     *         {@code 4xx} whose RFC 6749 §5.2 body names {@code invalid_grant}, so the server declared
     *         the presented credential dead without redeeming it; and
     *         {@link RedeemedResponseException} when the body cannot be parsed <em>after</em> a success
     *         status, because by then the server has already redeemed the grant. A success body whose
     *         top-level JSON object repeats a member name is likewise a {@link RedeemedResponseException}:
     *         neither the first nor the last occurrence is accepted. A body that cannot be read at all
     *         after a success status line — one larger than the payload bound, for example — is a
     *         {@link RedeemedResponseException} too, never a pre-redemption failure. Under a DPoP
     *         {@code senderConstraint} a {@link RedeemedResponseException} is also raised when the
     *         issued token is not bound to the proof key: the {@code token_type} is not {@code DPoP}
     *         (a {@code Bearer} downgrade), or the access token is a JWT whose {@code cnf.jkt} is absent
     *         or differs from the proof-key thumbprint, or whose {@code cnf} claim is not an object
     *         carrying a string {@code jkt}. A body the JSON library cannot read — including one
     *         truncated inside a string escape — never escapes as an unchecked exception: after a
     *         success status it is a {@link RedeemedResponseException}, and as a non-success error
     *         body it is an unknown error code, so the failure is a plain {@link TransportException}
     *         and never a {@link CredentialRejectedException}. A non-success error body whose top-level
     *         JSON object repeats a member name is likewise an unknown error code, never a
     *         {@link CredentialRejectedException}
     */
    public TokenResponse requestToken(String tokenEndpoint,
            Map<String, String> formParameters,
            Map<String, String> requestHeaders,
            @Nullable SenderConstraint senderConstraint) {
        Objects.requireNonNull(tokenEndpoint, "tokenEndpoint must not be null");
        Objects.requireNonNull(formParameters, "formParameters must not be null");
        Objects.requireNonNull(requestHeaders, "requestHeaders must not be null");

        HttpHandler handler = backChannel.validatedHandler(tokenEndpoint, FAILURE_CONTEXT);
        HttpClient client = backChannel.sharedClient(handler);
        String body = FormEncoder.encode(formParameters);
        String targetUri = handler.getUri().toString();

        HttpResponse<String> response = send(handler, client, body,
                proofHeaders(targetUri, requestHeaders, senderConstraint, null));
        if (!HttpStatusFamily.isSuccess(response.statusCode())) {
            Optional<String> nonce = dpopNonceChallenge(response, senderConstraint);
            if (nonce.isPresent()) {
                response = send(handler, client, body,
                        proofHeaders(targetUri, requestHeaders, senderConstraint, nonce.get()));
            }
        }
        if (!HttpStatusFamily.isSuccess(response.statusCode())) {
            if (credentialRejected(response)) {
                // The detail message is composed from the observed status and fixed text only: no part
                // of the AS-controlled error body is interpolated into it (CWE-117), which is what
                // keeps this exception free of AS-controlled state.
                throw new CredentialRejectedException(
                        "Token endpoint rejected the presented credential with HTTP "
                                + response.statusCode() + " (RFC 6749 §5.2 'invalid_grant')");
            }
            throw new TransportException(
                    "Token endpoint returned unexpected HTTP status " + response.statusCode());
        }
        TokenResponse tokenResponse = parse(response.body());
        verifySenderConstraint(tokenResponse, senderConstraint);
        return tokenResponse;
    }

    /**
     * Verifies that a token issued under a DPoP sender-constraint is actually bound to the proof key
     * (RFC 9449 §5 and §6.1). mTLS constraints and unconstrained requests are not checked here: an
     * mTLS-bound token is presented as {@code Bearer} and its certificate binding is transport-level.
     * <p>
     * Under a DPoP constraint two conditions must hold, and either failure is fatal:
     * <ol>
     *   <li>the {@code token_type} is {@code DPoP} (case-insensitive) — a {@code Bearer} token issued in
     *       answer to a DPoP-proofed request is a downgrade to an unbound token;</li>
     *   <li>when the access token decodes as a compact JWS whose payload is a JSON object, its
     *       {@code cnf.jkt} is present and equals the proof-key thumbprint recorded in the constraint's
     *       binding — a JWT without {@code cnf.jkt}, or whose {@code cnf} is not an object or whose
     *       {@code jkt} is not a string, is not bound to the proof key, and one with a different
     *       {@code jkt} is bound to somebody else's key.</li>
     * </ol>
     * An access token that does not decode as a JWS whose payload is a JSON object (an opaque reference
     * token, a JWE, a malformed value) skips only the {@code cnf.jkt} comparison; the {@code token_type}
     * condition still applies. The check runs after a success status, so the grant is already redeemed and every
     * rejection is a {@link RedeemedResponseException}. Exception messages carry fixed text plus
     * sanitized (CWE-117) authorization-server values only.
     */
    private void verifySenderConstraint(TokenResponse tokenResponse, @Nullable SenderConstraint senderConstraint) {
        if (senderConstraint == null || senderConstraint.method() != ConstraintBinding.Method.DPOP) {
            return;
        }
        if (!TokenResponse.TOKEN_TYPE_DPOP.equalsIgnoreCase(tokenResponse.tokenType)) {
            throw new RedeemedResponseException(
                    "Token endpoint issued token_type '" + LogSanitizer.sanitize(tokenResponse.tokenType)
                            + "' in answer to a DPoP-constrained request; expected 'DPoP'");
        }
        Optional<byte[]> payload = jwtPayload(tokenResponse.accessToken);
        if (payload.isEmpty()) {
            return;
        }
        String expectedJkt = senderConstraint.binding().confirmation();
        Optional<String> issuedJkt = readConfirmation(payload.get()).getJkt();
        if (issuedJkt.isEmpty()) {
            throw new RedeemedResponseException(
                    "Token endpoint issued a DPoP JWT access token without a cnf.jkt binding");
        }
        if (!expectedJkt.equals(issuedJkt.get())) {
            throw new RedeemedResponseException(
                    "Token endpoint issued a DPoP JWT access token bound to cnf.jkt '"
                            + LogSanitizer.sanitize(issuedJkt.get())
                            + "', which does not match the DPoP proof-key thumbprint");
        }
    }

    /**
     * Decides whether the access token is a parseable JWT: a compact JWS (exactly three dot-separated
     * parts) whose payload part is non-empty base64url that decodes to a syntactically valid JSON
     * object. This is a decode-only step for the issuance-time binding check — the token's signature
     * and claims are never validated here, and the probe reads no member of the object.
     * <p>
     * No size guard is applied to the decoded payload: {@link #parse(String)} already rejects any
     * response body larger than the configured payload bound, the access token is a substring of that
     * body, and base64url decoding shrinks its input, so the decoded payload is always within the bound.
     *
     * @return the decoded payload bytes when the access token is a JWT, or empty when it is not (an
     *         opaque token, a JWE, a value with the wrong part count, an empty or non-base64url payload
     *         part, or a payload that is not a JSON object) — only then is the {@code cnf.jkt}
     *         comparison skipped
     */
    private Optional<byte[]> jwtPayload(String accessToken) {
        String[] parts = accessToken.split("\\.", -1);
        if (parts.length != 3 || parts[1].isEmpty()) {
            return Optional.empty();
        }
        byte[] payload;
        try {
            payload = Base64.getUrlDecoder().decode(parts[1]);
        } catch (IllegalArgumentException e) {
            LOGGER.debug("Access token payload is not base64url; skipping the cnf.jkt comparison");
            return Optional.empty();
        }
        try {
            if (deserialize(AccessTokenConfirmation.JsonObjectProbe.class, payload) == null) {
                LOGGER.debug("Access token payload is JSON null; skipping the cnf.jkt comparison");
                return Optional.empty();
            }
        } catch (IOException e) {
            LOGGER.debug("Access token payload is not a JSON object; skipping the cnf.jkt comparison: %s",
                    LogSanitizer.sanitize(e.getMessage()));
            return Optional.empty();
        }
        return Optional.of(payload);
    }

    /**
     * Reads the {@code cnf} claim of a payload {@link #jwtPayload(String)} already established to be a
     * JSON object. The read is strict: the access token is known to be a JWT, so a {@code cnf} that is
     * not an object, or a {@code jkt} that is not a string, is an unbound token and fails closed — it
     * never degrades into the "not a JWT" skip.
     *
     * @return the confirmation view; its {@code jkt} is empty when {@code cnf} or {@code cnf.jkt} is
     *         absent or {@code null}
     * @throws RedeemedResponseException when {@code cnf} cannot be read as an object carrying a string
     *         {@code jkt}
     */
    private AccessTokenConfirmation readConfirmation(byte[] payload) {
        try {
            AccessTokenConfirmation confirmation = deserialize(AccessTokenConfirmation.class, payload);
            return confirmation == null ? new AccessTokenConfirmation() : confirmation;
        } catch (IOException e) {
            LOGGER.debug("Access token cnf claim is malformed: %s", LogSanitizer.sanitize(e.getMessage()));
            throw new RedeemedResponseException(
                    "Token endpoint issued a DPoP JWT access token whose cnf claim is not an object with a string jkt",
                    e);
        }
    }

    /**
     * Whether the authorization server attributed this refusal to the presented credential itself.
     * <p>
     * The predicate is an <strong>allow-list of exactly one error code</strong>: the status is a client
     * error ({@code 4xx}) AND the body parses as an RFC 6749 §5.2 error response AND its {@code error}
     * member is exactly {@value #ERROR_INVALID_GRANT}. Everything else — an unparseable body, an absent
     * body, a body with no {@code error} member, an oversize body, an unrecognized code, and any
     * {@code 5xx} even when it carries an {@code invalid_grant} body — answers {@code false} and is
     * reported as an ordinary transport failure.
     * <p>
     * The asymmetry is deliberate and the fail-safe direction is to preserve the session. Reporting a
     * transient fault as a dead credential destroys a working session; reporting a dead credential as a
     * transient fault merely delays the discovery until the next refresh. A {@code 5xx} is the server
     * saying it failed, not that this credential is bad, so the {@code error} body it happens to carry
     * is not evidence about the credential.
     */
    private boolean credentialRejected(HttpResponse<String> response) {
        if (!HttpStatusFamily.isClientError(response.statusCode())) {
            return false;
        }
        return ERROR_INVALID_GRANT.equals(errorCode(response.body()));
    }

    /**
     * @return the RFC 6749 §5.2 {@code error} code of an error response body, or {@code null} when the
     *         body is absent, blank, larger than the configured payload bound, unparseable, repeats a
     *         top-level member name, or carries no {@code error} member. Every one of those is an
     *         "unknown", never a match — the allow-list above only ever fires on a positively
     *         recognized code. A repeated member (for example two {@code error} members) leaves the
     *         effective value to the parser's first-wins or last-wins choice, so the body is ambiguous
     *         and is not read as any code
     */
    @Nullable
    private String errorCode(@Nullable String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxContentSize) {
            return null;
        }
        try {
            Optional<String> duplicateMember = duplicateTopLevelMember(bytes);
            if (duplicateMember.isPresent()) {
                // The member name is AS-controlled, so sanitize it (CWE-117) before it reaches the log.
                LOGGER.debug("Token endpoint error response repeats the top-level member '%s'; "
                        + "treating its error code as unknown", LogSanitizer.sanitize(duplicateMember.get()));
                return null;
            }
            TokenErrorResponse errorResponse = deserialize(TokenErrorResponse.class, bytes);
            return errorResponse == null ? null : errorResponse.error;
        } catch (IOException e) {
            // The parse-error message can echo an AS-controlled JSON fragment; sanitize it (CWE-117)
            // before it reaches the log appender, matching the success-path parse() site.
            LOGGER.debug("Failed to parse token endpoint error response: %s",
                    LogSanitizer.sanitize(e.getMessage()));
            return null;
        }
    }

    /**
     * Composes the request headers for one attempt: the caller-supplied headers plus, when a DPoP
     * constraint is present, a fresh single-use {@code DPoP} proof whose {@code htu} is
     * {@code targetUri}, the URL actually contacted (optionally echoing a challenged {@code nonce}). A
     * fresh map is returned per attempt so the caller's header map is never mutated and each retry
     * carries its own single-use proof.
     */
    private static Map<String, String> proofHeaders(String targetUri, Map<String, String> requestHeaders,
            @Nullable SenderConstraint senderConstraint, @Nullable String nonce) {
        Map<String, String> headers = new HashMap<>(requestHeaders);
        if (senderConstraint != null) {
            senderConstraint.apply(HTTP_POST, targetUri, nonce, headers);
        }
        return headers;
    }

    /**
     * @return the {@code DPoP-Nonce} value when the failed response is a recoverable
     *         {@code use_dpop_nonce} challenge against a DPoP constraint, otherwise empty
     */
    private static Optional<String> dpopNonceChallenge(HttpResponse<String> response,
            @Nullable SenderConstraint senderConstraint) {
        if (senderConstraint == null || senderConstraint.method() != ConstraintBinding.Method.DPOP) {
            return Optional.empty();
        }
        return response.headers().firstValue(DPOP_NONCE_HEADER)
                .filter(nonce -> !nonce.isBlank());
    }

    private HttpResponse<String> send(HttpHandler handler, HttpClient client, String body,
            Map<String, String> requestHeaders) {
        HttpRequest.Builder requestBuilder = handler.requestBuilder()
                .setHeader(CONTENT_TYPE, FORM_URLENCODED)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        requestHeaders.forEach(requestBuilder::setHeader);
        HttpResponse.BodyHandler<String> boundedBody = backChannel.bodyHandler();
        AtomicInteger observedStatus = new AtomicInteger(NO_STATUS);
        try {
            return client.send(requestBuilder.build(), responseInfo -> {
                observedStatus.set(responseInfo.statusCode());
                return boundedBody.apply(responseInfo);
            });
        } catch (IOException e) {
            LOGGER.debug(e, "Token endpoint request failed: %s", e.getMessage());
            if (HttpStatusFamily.isSuccess(observedStatus.get())) {
                // The status line was a success before the body read failed (for example a body past
                // the payload bound): the server has already redeemed the grant, so this is a
                // post-redemption failure, never a pre-redemption one.
                throw new RedeemedResponseException("Token endpoint response body could not be read after HTTP "
                        + observedStatus.get() + ": " + e.getMessage(), e);
            }
            throw new TransportException("Token endpoint request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransportException("Token endpoint request interrupted", e);
        }
    }

    // Every branch below runs only after HttpStatusFamily.isSuccess(...) held for the response, so the
    // authorization server has already accepted the request and consumed the presented grant. Each
    // therefore raises RedeemedResponseException — a TransportException subtype, so every existing
    // caller and every documented @throws TransportException contract is unchanged — which is what
    // lets a caller that must fail closed tell a post-redemption failure from a pre-redemption one.
    //
    // java:S2589 — body is non-null by every current caller/handler wiring, but the guard is kept
    // (consistent with the equivalent parse() entry guards in UserInfoClient / ParClient) as
    // defensive resilience against a future change to the shared bounded body-handler.
    @SuppressWarnings("java:S2589")
    private TokenResponse parse(String body) {
        if (body == null || body.isBlank()) {
            throw new RedeemedResponseException("Empty token endpoint response");
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxContentSize) {
            throw new RedeemedResponseException("Token endpoint response exceeds maximum allowed size of "
                    + maxContentSize + " bytes (" + BackChannelHttp.FIXED_PARSER_CONFIG_ORIGIN + ")");
        }
        try {
            Optional<String> duplicateMember = duplicateTopLevelMember(bytes);
            if (duplicateMember.isPresent()) {
                // A repeated member leaves the effective value to the parser's last-wins or first-wins
                // choice, so neither copy is trustworthy: refuse the body. The member name is
                // AS-controlled, so sanitize it (CWE-117) before it reaches the exception message.
                throw new RedeemedResponseException("Token endpoint response repeats the top-level member '"
                        + LogSanitizer.sanitize(duplicateMember.get()) + "'");
            }
            TokenResponse tokenResponse = deserialize(TokenResponse.class, bytes);
            if (tokenResponse == null || tokenResponse.accessToken == null) {
                throw new RedeemedResponseException("Token endpoint response is missing the access_token");
            }
            if (!tokenResponse.hasRecognizedTokenType()) {
                // RFC 6749 §5.1 makes token_type REQUIRED; a missing or unrecognized type must not be
                // silently consumed as Bearer. tokenType is AS-controlled, so sanitize it (CWE-117)
                // before it reaches the exception message on a plain-text appender path.
                throw new RedeemedResponseException("Token endpoint response has an unsupported token_type: "
                        + LogSanitizer.sanitize(tokenResponse.tokenType));
            }
            return tokenResponse;
        } catch (IOException e) {
            // The parse-error message can echo an AS-controlled JSON fragment; sanitize it (CWE-117)
            // before it reaches the log appender or the exception message, matching the token_type site.
            String sanitizedError = LogSanitizer.sanitize(e.getMessage());
            LOGGER.debug("Failed to parse token endpoint response: %s", sanitizedError);
            throw new RedeemedResponseException("Failed to parse token endpoint response: " + sanitizedError, e);
        }
    }

    /**
     * Walks the member names of the top-level JSON object, skipping every value without materializing
     * it, and reports the first name that occurs twice. Only the top level is inspected: nested objects
     * are skipped as values. A body whose top level is not an object is left to the deserializer, which
     * already rejects it.
     * <p>
     * The caller has already bounded {@code bytes} by the configured payload size, so the walk is a
     * bounded second pass. Input the reader cannot walk — including input the library signals with an
     * unchecked exception — is reported as an {@link IOException}, so it takes the caller's existing
     * parse-failure path.
     *
     * @return the first repeated top-level member name, or empty when every name is unique
     * @throws IOException when the top-level object cannot be walked
     */
    private Optional<String> duplicateTopLevelMember(byte[] bytes) throws IOException {
        try {
            JsonReader<Object> reader = dslJson.newReader(bytes, bytes.length);
            if (reader.getNextToken() != '{' || reader.getNextToken() == '}') {
                return Optional.empty();
            }
            Set<String> names = new HashSet<>();
            while (true) {
                String name = reader.readKey();
                if (!names.add(name)) {
                    return Optional.of(name);
                }
                byte next = reader.skip();
                if (next == '}') {
                    return Optional.empty();
                }
                if (next != ',') {
                    throw new IOException("Expecting ',' or '}' after a top-level member value");
                }
                reader.getNextToken();
            }
        }
        // cui-rewrite:disable InvalidExceptionUsageRecipe
        catch (RuntimeException e) {
            // Same deliberate broad catch as deserialize(): the reader signals some unreadable input with
            // unchecked exceptions outside its contract.
            throw new IOException("Unreadable JSON (" + e.getClass().getSimpleName() + ")", e);
        }
    }

    /**
     * The single JSON deserialize seam for every authorization-server-controlled payload this client
     * reads. DSL-JSON reports some malformed input — for example a body truncated inside a string
     * escape, i.e. ending in a backslash within an unterminated string — with an unchecked exception (an
     * {@link ArrayIndexOutOfBoundsException}) instead of a parse error. That exception is folded into
     * the {@link IOException} every call site already handles, so an unreadable payload always takes
     * the site's existing parse-failure path and never escapes the documented
     * {@link TransportException} / {@link RedeemedResponseException} contract after the grant was
     * redeemed. The fold changes no acceptance decision: input the library reads is returned as before.
     * The wrapping message is fixed text only; the cause is attached.
     *
     * @return the deserialized value, or {@code null} when the payload is the JSON literal {@code null}
     * @throws IOException when the payload cannot be read as {@code type}, whether the library signalled
     *         that with a parse error or with an unchecked exception
     */
    @Nullable
    private <T> T deserialize(Class<T> type, byte[] bytes) throws IOException {
        try {
            return dslJson.deserialize(type, bytes, bytes.length);
        }
        // cui-rewrite:disable InvalidExceptionUsageRecipe
        catch (RuntimeException e) {
            // Deliberate broad catch: the JSON library signals a payload it cannot read with unchecked
            // exceptions that are not part of its contract (a body cut off inside a string escape throws
            // ArrayIndexOutOfBoundsException), so no specific type can be named without leaving a hole.
            throw new IOException("Unreadable JSON (" + e.getClass().getSimpleName() + ")", e);
        }
    }

}
