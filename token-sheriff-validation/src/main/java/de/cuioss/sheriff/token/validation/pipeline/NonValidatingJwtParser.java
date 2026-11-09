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
package de.cuioss.sheriff.token.validation.pipeline;

import com.dslplatform.json.DslJson;
import de.cuioss.sheriff.token.commons.events.SecurityEventCounter;
import de.cuioss.sheriff.token.commons.transport.ParserConfig;
import de.cuioss.sheriff.token.validation.JWTValidationLogMessages;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.sheriff.token.validation.json.JwtHeader;
import de.cuioss.sheriff.token.validation.json.MapRepresentation;
import de.cuioss.sheriff.token.validation.jwe.JweDecryptionConfig;
import de.cuioss.sheriff.token.validation.jwe.JweDecryptor;
import de.cuioss.tools.base.Preconditions;
import de.cuioss.tools.logging.CuiLogger;
import de.cuioss.tools.logging.LogRecord;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/**
 * This class provides a unified way to parse JWT tokens and extract common information
 * such as the header, body, signature, issuer, and kid-header.
 * <p>
 * Security features:
 * <ul>
 *   <li>Token size validation to prevent memory exhaustion</li>
 *   <li>Payload size validation for JSON parsing</li>
 *   <li>Standard Base64 decoding for JWT parts</li>
 *   <li>Proper character encoding handling</li>
 *   <li>JSON depth limits to prevent stack overflow attacks</li>
 *   <li>JSON array size limits to prevent denial-of-service attacks</li>
 *   <li>JSON string size limits to prevent memory exhaustion</li>
 * </ul>
 * <p>
 * The examples below illustrate the API; they are not compiled or run as tests themselves.
 * <p>
 * Basic usage example:
 * {@snippet :
 * // Create a parser with default settings
 * NonValidatingJwtParser parser = NonValidatingJwtParser.builder()
 *     .securityEventCounter(securityEventCounter)
 *     .build();
 *
 * try {
 *     // Decode a JWT token; parsing failures can throw a TokenValidationException.
 *     // This parser does NOT validate the signature, and a payload of JSON null
 *     // (e.g. Base64Url "bnVsbA") decodes to an empty body.
 *     DecodedJwt jwt = parser.decode(tokenString);
 *
 *     // Access common JWT fields with convenience methods
 *     jwt.getAlg().ifPresent(alg -> LOGGER.debug("Algorithm: %s", alg));
 *     jwt.getIssuer().ifPresent(issuer -> LOGGER.debug("Issuer: %s", issuer));
 *     jwt.getKid().ifPresent(kid -> LOGGER.debug("Key ID: %s", kid));
 *
 *     // Access the raw token
 *     String rawToken = jwt.rawToken();
 *
 *     // For more complex operations, access the header and body via record accessors;
 *     // both are guaranteed to be non-null
 *     JwtHeader header = jwt.header();
 *     MapRepresentation body = jwt.body();
 *     Optional<String> customValue = body.getString("custom_claim");
 * } catch (TokenValidationException e) {
 *     LOGGER.debug("Token rejected: %s", e.getEventType());
 * }
 * }
 * <p>
 * Example with custom security settings:
 * {@snippet :
 * // Create a parser with custom security settings using ParserConfig
 * ParserConfig config = ParserConfig.builder()
 *     .maxTokenSize(1024)      // 1KB max token size
 *     .maxPayloadSize(512)     // 512 bytes max payload size
 *     .maxStringLength(256)    // 256 bytes max string size
 *     .maxArraySize(10)        // 10 elements max array size
 *     .maxNestingDepth(5)      // 5 levels max JSON nesting depth
 *     .build();
 *
 * NonValidatingJwtParser customParser = NonValidatingJwtParser.builder()
 *     .config(config)
 *     .securityEventCounter(securityEventCounter)
 *     .build();
 *
 * // Decode a token with the custom parser
 * DecodedJwt result = customParser.decode(tokenString);
 * }
 * <p>
 * Example handling invalid tokens (passing a {@code null} or empty token to the parser throws an
 * {@link IllegalArgumentException}, which {@code TokenStringValidator} rules out on every production path;
 * an oversized token is rejected with event type {@code TOKEN_SIZE_EXCEEDED}. The size limit is
 * {@link ParserConfig#getMaxTokenSize()}, except for a 5-part JWE when JWE decryption is configured, which
 * is bound by the effective JWE limit {@link JweDecryptionConfig#getMaxEncryptedTokenSize()}. A size
 * pre-check also runs in {@code TokenStringValidator} at the start of all three pipelines (access, ID and
 * refresh token), before this parser is reached; when JWE decryption is configured it applies the JWE
 * limit to every token, so a JWS between the two limits is rejected by this parser):
 * {@snippet :
 * // An invalid token format is rejected, never returned
 * try {
 *     parser.decode("invalid.token.format");
 * } catch (TokenValidationException e) {
 *     // The event type names the reason, e.g. INVALID_JWT_FORMAT or FAILED_TO_DECODE_JWT
 *     SecurityEventCounter.EventType reason = e.getEventType();
 * }
 * }
 * <p>
 * For more details on the security aspects, see the
 * <a href="https://github.com/cuioss/TokenSheriff/tree/main/doc/validation/security-reference.adoc">Security Specification</a>
 *
 * @apiNote This class is internal to Token-Sheriff and not part of the public API.
 * @since 1.0
 * @author Oliver Wolff
 */
@ToString
@EqualsAndHashCode
public class NonValidatingJwtParser {

    private static final CuiLogger LOGGER = new CuiLogger(NonValidatingJwtParser.class);

    /**
     * Detail for the {@code INVALID_JWT_FORMAT} record when a 3-part JWS has an empty signature segment.
     */
    private static final String EMPTY_SIGNATURE_SEGMENT = "3 parts with an empty signature segment";

    /**
     * Configuration for the parser, containing all security settings.
     */
    private final ParserConfig config;

    /**
     * Counter for security events that occur during token processing.
     */
    private final SecurityEventCounter securityEventCounter;

    /**
     * Optional JWE decryption configuration. When present, 5-part JWE tokens
     * will be decrypted and the inner JWS will be parsed.
     */
    @Nullable
    private final JweDecryptionConfig jweDecryptionConfig;

    private final JweDecryptor jweDecryptor;

    private NonValidatingJwtParser(ParserConfig config, SecurityEventCounter securityEventCounter,
            @Nullable JweDecryptionConfig jweDecryptionConfig) {
        this.config = Objects.requireNonNull(config, "ParserConfig must not be null");
        this.securityEventCounter = securityEventCounter;
        this.jweDecryptionConfig = jweDecryptionConfig;
        this.jweDecryptor = jweDecryptionConfig != null ? new JweDecryptor() : null;
    }

    public static NonValidatingJwtParserBuilder builder() {
        return new NonValidatingJwtParserBuilder();
    }

    public static class NonValidatingJwtParserBuilder {
        private ParserConfig config = ParserConfig.builder().build();
        private SecurityEventCounter securityEventCounter;
        private JweDecryptionConfig jweDecryptionConfig;

        public NonValidatingJwtParserBuilder config(ParserConfig config) {
            this.config = config;
            return this;
        }

        public NonValidatingJwtParserBuilder securityEventCounter(SecurityEventCounter securityEventCounter) {
            this.securityEventCounter = securityEventCounter;
            return this;
        }

        public NonValidatingJwtParserBuilder jweDecryptionConfig(JweDecryptionConfig jweDecryptionConfig) {
            this.jweDecryptionConfig = jweDecryptionConfig;
            return this;
        }

        public NonValidatingJwtParser build() {
            Objects.requireNonNull(securityEventCounter, "SecurityEventCounter must not be null");
            return new NonValidatingJwtParser(config, securityEventCounter, jweDecryptionConfig);
        }
    }

    /**
     * Decodes a JWT token and returns a DecodedJwt object containing the decoded parts.
     * <p>
     * Security considerations:
     * <ul>
     *   <li>Does not validate signatures - use only for inspection</li>
     *   <li>Implements size checks to prevent overflow attacks</li>
     *   <li>Uses standard Java Base64 decoder</li>
     * </ul>
     * <p>
     * This method logs warnings when decoding fails. Use {@link #decodeQuietly(String)}
     * to suppress warning logging.
     *
     * @param token the JWT token string to parse
     * @return the DecodedJwt if parsing is successful
     * @throws IllegalArgumentException if the token is {@code null} or empty (ruled out by
     *         {@code TokenStringValidator} on every production path)
     * @throws TokenValidationException if the token is invalid or cannot be parsed; the rejection is
     *         logged and counted
     */
    public DecodedJwt decode(String token) {
        return decodeInternal(token, DecodeMode.NORMAL);
    }

    /**
     * Decodes a JWT token without logging warnings or tracking security events.
     * <p>
     * This method is specifically designed for opaque tokens (like refresh tokens) where
     * parsing failures are expected and should not be logged or tracked as security events.
     * <p>
     * Security considerations:
     * <ul>
     *   <li>Does not validate signatures - use only for inspection</li>
     *   <li>Implements size checks to prevent overflow attacks</li>
     *   <li>Uses standard Java Base64 decoder</li>
     *   <li>No logging or security event tracking on failures</li>
     * </ul>
     *
     * @param token the JWT token string to parse (may be opaque)
     * @return the DecodedJwt if parsing is successful
     * @throws IllegalArgumentException if the token is {@code null} or empty (ruled out by
     *         {@code TokenStringValidator} on every production path)
     * @throws TokenValidationException if the token is invalid or cannot be parsed; the rejection is
     *         neither logged nor counted
     */
    public DecodedJwt decodeOpaqueToken(String token) {
        return decodeInternal(token, DecodeMode.OPAQUE);
    }

    /**
     * Decodes a JWT token without logging warnings, but still tracking security events.
     * <p>
     * This method is useful when re-parsing a previously validated token (e.g., for
     * DPoP validation of cached tokens) where decoding warnings are not desired.
     * <p>
     * Security considerations:
     * <ul>
     *   <li>Does not validate signatures - use only for inspection</li>
     *   <li>Implements size checks to prevent overflow attacks</li>
     *   <li>Uses standard Java Base64 decoder</li>
     * </ul>
     *
     * @param token the JWT token string to parse
     * @return the DecodedJwt if parsing is successful
     * @throws TokenValidationException if the token is invalid or cannot be parsed
     */
    DecodedJwt decodeQuietly(String token) {
        return decodeInternal(token, DecodeMode.QUIET);
    }

    /**
     * Decode mode controlling logging and security event tracking behavior.
     * <ul>
     *   <li>{@code NORMAL} — log warnings and track security events (standard validation path)</li>
     *   <li>{@code QUIET} — no logging, but track security events (re-parsing cached tokens)</li>
     *   <li>{@code OPAQUE} — no logging, no tracking (opaque/refresh tokens where failures are expected)</li>
     * </ul>
     */
    private enum DecodeMode {
        NORMAL(true, true),
        QUIET(false, true),
        OPAQUE(false, false);

        private final boolean logWarnings;
        private final boolean trackSecurityEvents;

        DecodeMode(boolean logWarnings, boolean trackSecurityEvents) {
            this.logWarnings = logWarnings;
            this.trackSecurityEvents = trackSecurityEvents;
        }
    }

    /**
     * Internal method that handles token decoding with configurable logging and security tracking.
     *
     * @param token the JWT token string to parse
     * @param mode the decode mode controlling logging and security event tracking
     * @return the DecodedJwt if parsing is successful
     * @throws TokenValidationException if the token is invalid or cannot be parsed
     */
    @SuppressWarnings("java:S3776") // owolff: Justified - complexity due to logging and security event tracking
    private DecodedJwt decodeInternal(String token, DecodeMode mode) {
        // Precondition: TokenStringValidator has already rejected a null or blank token
        Preconditions.checkArgument(token != null && !token.isEmpty(),
                "TokenStringValidator precondition: token must not be null/empty");

        // Size limit: a 5-part compact JWE is bound by the JWE limit when JWE is configured, every other
        // token by the parser's own limit. TokenStringValidator applies the JWE limit to every token
        // when JWE is configured, so a JWS between the two limits reaches this check and is rejected
        // here as a typed, accounted rejection rather than escaping as an unchecked exception.
        int tokenSize = token.getBytes(StandardCharsets.UTF_8).length;
        int sizeLimit = sizeLimitFor(token);
        if (tokenSize > sizeLimit) {
            throw rejectAccounted(SecurityEventCounter.EventType.TOKEN_SIZE_EXCEEDED,
                    "Token size %d exceeds maximum %d".formatted(tokenSize, sizeLimit),
                    mode.logWarnings, mode.trackSecurityEvents,
                    JWTValidationLogMessages.WARN.TOKEN_SIZE_EXCEEDED, sizeLimit);
        }

        // Split token and validate format. The negative limit keeps trailing empty segments, so a
        // trailing dot ("h.p.s.") adds a part instead of being silently dropped.
        String[] parts = token.split("\\.", -1);

        if (parts.length == 5) {
            // JWE token (5 parts: header.encryptedKey.iv.ciphertext.authTag)
            return handleJweToken(parts, token, mode.logWarnings, mode.trackSecurityEvents);
        }

        if (parts.length != 3) {
            throw rejectInvalidFormat(parts.length, mode.logWarnings, mode.trackSecurityEvents);
        }
        if (parts[2].isEmpty()) {
            // "h.p." is a JWS without signature; reject it as before rather than accepting an unsigned shape
            throw rejectInvalidFormat(EMPTY_SIGNATURE_SEGMENT, mode.logWarnings, mode.trackSecurityEvents);
        }

        try {
            // Decode token parts
            return decodeTokenParts(parts, token, mode.logWarnings, mode.trackSecurityEvents);
        } catch (IllegalArgumentException e) {
            if (mode.logWarnings) {
                LOGGER.warn(e, JWTValidationLogMessages.WARN.FAILED_TO_DECODE_JWT);
            }
            if (mode.trackSecurityEvents) {
                securityEventCounter.increment(SecurityEventCounter.EventType.FAILED_TO_DECODE_JWT);
            }
            throw new TokenValidationException(
                    SecurityEventCounter.EventType.FAILED_TO_DECODE_JWT,
                    "Failed to decode JWT: %s".formatted(e.getMessage()),
                    e
            );
        }
    }

    /**
     * Returns the size limit in bytes that applies to {@code token}: the configured
     * {@link JweDecryptionConfig#getMaxEncryptedTokenSize() JWE limit} for a 5-part compact token when
     * JWE decryption is configured, {@link ParserConfig#getMaxTokenSize()} otherwise. The inner JWS of a
     * JWE is checked against {@link ParserConfig#getMaxTokenSize()} after decryption.
     *
     * @param token the raw token
     * @return the applicable size limit in bytes
     */
    private int sizeLimitFor(String token) {
        if (jweDecryptionConfig != null && isFivePartCompact(token)) {
            return jweDecryptionConfig.getMaxEncryptedTokenSize();
        }
        return config.getMaxTokenSize();
    }

    /**
     * @param token the raw token
     * @return whether the token has exactly the four separators of a 5-part compact serialisation
     */
    private static boolean isFivePartCompact(String token) {
        int separators = 0;
        for (int i = 0; i < token.length(); i++) {
            if (token.charAt(i) == '.' && ++separators > 4) {
                return false;
            }
        }
        return separators == 4;
    }

    /**
     * Handles a 5-part JWE token by decrypting it and parsing the inner JWS.
     */
    private DecodedJwt handleJweToken(String[] parts, String originalToken,
            boolean logWarnings, boolean trackSecurityEvents) {
        validateJweConfigured(logWarnings, trackSecurityEvents);

        try {
            // Decode JWE header only (first part)
            JwtHeader jweHeader = decodeJwtHeader(parts[0], logWarnings, trackSecurityEvents);

            // Verify this is actually a JWE (has 'enc' field)
            if (!jweHeader.isJwe()) {
                throw rejectAccounted(SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED,
                        "5-part token does not have 'enc' header field — not a valid JWE",
                        logWarnings, trackSecurityEvents,
                        JWTValidationLogMessages.WARN.JWE_DECRYPTION_FAILED, "missing 'enc' header field");
            }

            // Decrypt to get inner JWS string. The decryptor honours this decode mode, so an
            // opaque decode stays free of logging and counting, and a quiet decode only counts.
            String innerJws = jweDecryptor.decrypt(parts, jweHeader, jweDecryptionConfig, securityEventCounter,
                    logWarnings, trackSecurityEvents);

            // Check inner JWS size
            if (innerJws.getBytes(StandardCharsets.UTF_8).length > config.getMaxTokenSize()) {
                throw rejectAccounted(SecurityEventCounter.EventType.TOKEN_SIZE_EXCEEDED,
                        "Inner JWS from JWE exceeds maximum token size",
                        logWarnings, trackSecurityEvents,
                        JWTValidationLogMessages.WARN.TOKEN_SIZE_EXCEEDED, config.getMaxTokenSize());
            }

            // Parse the inner JWS and return DecodedJwt with original JWE as rawToken
            String[] innerParts = validateInnerJwsParts(innerJws, logWarnings, trackSecurityEvents);
            DecodedJwt innerDecoded = decodeTokenParts(innerParts, innerJws, logWarnings, trackSecurityEvents);
            // Return with original JWE token as rawToken (for caching/logging)
            return new DecodedJwt(innerDecoded.header(), innerDecoded.body(),
                    innerDecoded.signature(), innerDecoded.parts(), originalToken);

        } catch (TokenValidationException e) {
            throw e;
        } catch (IOException | ArrayIndexOutOfBoundsException e) {
            // A truncated escape in the JWE header surfaces from DSL-JSON as
            // ArrayIndexOutOfBoundsException; it is rejected exactly like any other malformed header.
            // DSL-JSON's parse-error message quotes the raw input around the failure position,
            // i.e. unauthenticated header bytes including CR/LF (CWE-117). Neither the WARN nor
            // the exception message may carry it; the cause chain keeps it for callers that
            // deliberately inspect it.
            throw rejectUndecodablePart("Failed to decode JWE header: malformed or structurally invalid header JSON",
                    e, logWarnings, trackSecurityEvents);
        }
    }

    /**
     * Validates that JWE decryption is configured, throwing if it is not.
     */
    private void validateJweConfigured(boolean logWarnings, boolean trackSecurityEvents) {
        if (jweDecryptionConfig == null || jweDecryptor == null) {
            if (logWarnings) {
                LOGGER.warn(JWTValidationLogMessages.WARN.JWE_DECRYPTION_NOT_CONFIGURED);
            }
            if (trackSecurityEvents) {
                securityEventCounter.increment(SecurityEventCounter.EventType.JWE_DECRYPTION_NOT_CONFIGURED);
            }
            throw new TokenValidationException(
                    SecurityEventCounter.EventType.JWE_DECRYPTION_NOT_CONFIGURED,
                    "Received JWE token but no decryption configuration is available");
        }
    }

    /**
     * Splits the inner JWS string and validates the part count.
     * Returns the 3 parts on success, throws {@link TokenValidationException} otherwise.
     * Each rejection is logged and counted exactly once according to the decode mode.
     *
     * @param innerJws             the decrypted inner JWS
     * @param logWarnings          whether a rejection is logged
     * @param trackSecurityEvents  whether a rejection is counted
     * @return the three parts of the inner JWS
     * @throws TokenValidationException if the inner token is a nested JWE or not a 3-part JWS
     */
    private String[] validateInnerJwsParts(String innerJws, boolean logWarnings, boolean trackSecurityEvents) {
        // Negative limit: keep trailing empty segments so a trailing dot is not silently dropped
        String[] innerParts = innerJws.split("\\.", -1);
        if (innerParts.length == 5) {
            throw rejectAccounted(SecurityEventCounter.EventType.JWE_DECRYPTION_FAILED,
                    "Nested JWE tokens are not allowed",
                    logWarnings, trackSecurityEvents, JWTValidationLogMessages.WARN.JWE_NESTED_NOT_ALLOWED);
        }
        if (innerParts.length != 3) {
            throw rejectAccounted(SecurityEventCounter.EventType.INVALID_JWT_FORMAT,
                    "Inner JWS from JWE has invalid format: expected 3 parts but got %d".formatted(innerParts.length),
                    logWarnings, trackSecurityEvents,
                    JWTValidationLogMessages.WARN.INVALID_JWT_FORMAT, innerParts.length);
        }
        if (innerParts[2].isEmpty()) {
            throw rejectAccounted(SecurityEventCounter.EventType.INVALID_JWT_FORMAT,
                    "Inner JWS from JWE has invalid format: " + EMPTY_SIGNATURE_SEGMENT,
                    logWarnings, trackSecurityEvents,
                    JWTValidationLogMessages.WARN.INVALID_JWT_FORMAT, EMPTY_SIGNATURE_SEGMENT);
        }
        return innerParts;
    }

    /**
     * Rejects a token, accounting for it according to the decode mode: the warning is written only
     * when {@code logWarnings} is set, and {@code eventType} is counted exactly once only when
     * {@code trackSecurityEvents} is set.
     *
     * @param eventType            the event type carried by the exception and counted
     * @param message              the exception message, free of raw token bytes
     * @param logWarnings          whether the rejection is logged
     * @param trackSecurityEvents  whether the rejection is counted
     * @param warning              the log record written when {@code logWarnings} is set
     * @param warningArgs          the parameters of {@code warning}
     * @return never returns normally; declared so callers can write {@code throw rejectAccounted(...)}
     * @throws TokenValidationException always, with the given event type and message
     */
    private TokenValidationException rejectAccounted(SecurityEventCounter.EventType eventType, String message,
            boolean logWarnings, boolean trackSecurityEvents, LogRecord warning, Object... warningArgs) {
        if (logWarnings) {
            LOGGER.warn(warning, warningArgs);
        }
        if (trackSecurityEvents) {
            securityEventCounter.increment(eventType);
        }
        throw new TokenValidationException(eventType, message);
    }

    /**
     * Rejects a token whose part structure is not a valid JWS or JWE, accounting for it according
     * to the decode mode.
     *
     * @param detail               what was found instead of a valid structure (part count or description)
     * @param logWarnings          whether the rejection is logged
     * @param trackSecurityEvents  whether the rejection is counted
     * @return never returns normally; declared so callers can write {@code throw rejectInvalidFormat(...)}
     * @throws TokenValidationException always, with event type {@code INVALID_JWT_FORMAT}
     */
    private TokenValidationException rejectInvalidFormat(Object detail, boolean logWarnings,
            boolean trackSecurityEvents) {
        if (logWarnings) {
            LOGGER.warn(JWTValidationLogMessages.WARN.INVALID_JWT_FORMAT, detail);
        }
        if (trackSecurityEvents) {
            securityEventCounter.increment(SecurityEventCounter.EventType.INVALID_JWT_FORMAT);
        }
        throw new TokenValidationException(
                SecurityEventCounter.EventType.INVALID_JWT_FORMAT,
                JWTValidationLogMessages.WARN.INVALID_JWT_FORMAT.format(detail));
    }


    /**
     * Decodes the token parts and creates a DecodedJwt object using DSL-JSON.
     *
     * @param parts                the token parts
     * @param token                the original token
     * @param logWarnings          whether to log warnings
     * @param trackSecurityEvents  whether to track security events
     * @return the DecodedJwt if decoding is successful
     * @throws TokenValidationException if decoding fails
     */
    private DecodedJwt decodeTokenParts(String[] parts, String token, boolean logWarnings, boolean trackSecurityEvents) {
        try {
            // Decode the header (first part) to JwtHeader using DSL-JSON
            JwtHeader header = decodeJwtHeader(parts[0], logWarnings, trackSecurityEvents);

            // Decode the payload (second part) to MapRepresentation using DSL-JSON
            MapRepresentation body = decodePayload(parts[1], logWarnings, trackSecurityEvents);

            // The signature part (third part) is kept as is
            String signature = parts[2];

            return new DecodedJwt(header, body, signature, parts, token);
        } catch (IOException | ArrayIndexOutOfBoundsException e) {
            // DSL-JSON reports a truncated escape (a trailing lone backslash, a cut-off unicode escape) by
            // reading past the end of its buffer, i.e. as ArrayIndexOutOfBoundsException rather than
            // IOException; it is the same malformed-input condition and takes the same rejection.
            // IOException from DSL-JSON deserialization. Its message quotes the raw input around
            // the failure position, i.e. unauthenticated header/payload bytes including CR/LF
            // (CWE-117). Neither the WARN nor the exception message may carry it; the cause chain
            // keeps it for callers that deliberately inspect it.
            throw rejectUndecodablePart("Failed to decode JWT parts: malformed or structurally invalid JSON",
                    e, logWarnings, trackSecurityEvents);
        }
    }

    /**
     * Decodes a Base64Url encoded JWT header using DSL-JSON.
     *
     * @param encodedHeader        the Base64Url encoded header part
     * @param logWarnings          whether a rejection is logged
     * @param trackSecurityEvents  whether a rejection is counted
     * @return the decoded JwtHeader
     * @throws IOException if DSL-JSON deserialization fails
     * @throws TokenValidationException if the part is not valid Base64Url or the header is JSON {@code null}
     */
    private JwtHeader decodeJwtHeader(String encodedHeader, boolean logWarnings, boolean trackSecurityEvents)
            throws IOException {
        byte[] jsonBytes = decodeBase64UrlPart(encodedHeader, logWarnings, trackSecurityEvents);
        DslJson<Object> dslJson = config.getDslJson();

        JwtHeader header = dslJson.deserialize(JwtHeader.class, jsonBytes, jsonBytes.length);

        if (header == null) {
            throw rejectUndecodablePart("Failed to deserialize JWT header: null result",
                    null, logWarnings, trackSecurityEvents);
        }

        return header;
    }

    /**
     * Decodes a Base64Url encoded JWT payload using DSL-JSON.
     *
     * @param encodedPayload       the Base64Url encoded payload part
     * @param logWarnings          whether a rejection is logged
     * @param trackSecurityEvents  whether a rejection is counted
     * @return the decoded MapRepresentation
     * @throws IOException if DSL-JSON deserialization fails
     * @throws TokenValidationException if the part is not valid Base64Url
     */
    private MapRepresentation decodePayload(String encodedPayload, boolean logWarnings, boolean trackSecurityEvents)
            throws IOException {
        byte[] jsonBytes = decodeBase64UrlPart(encodedPayload, logWarnings, trackSecurityEvents);
        DslJson<Object> dslJson = config.getDslJson();

        return MapRepresentation.fromJson(dslJson, jsonBytes);
    }

    /**
     * Decodes a Base64Url encoded part to its raw (UTF-8 encoded JSON) bytes.
     * <p>
     * Returning the raw bytes avoids a lossy byte-to-String-to-byte round trip and
     * guarantees that no platform-default charset is involved (JWT JSON is always UTF-8
     * per RFC 7515).
     * <p>
     * A part that is not valid Base64Url is rejected through
     * {@link #rejectUndecodablePart(String, Throwable, boolean, boolean)}, so it is logged and
     * counted exactly once according to the decode mode.
     *
     * @param encodedPart          the Base64Url encoded part
     * @param logWarnings          whether a Base64Url rejection is logged
     * @param trackSecurityEvents  whether a Base64Url rejection is counted
     * @return the decoded JSON bytes
     */
    private byte[] decodeBase64UrlPart(String encodedPart, boolean logWarnings, boolean trackSecurityEvents) {
        try {
            byte[] decodedBytes = Base64.getUrlDecoder().decode(encodedPart);

            // Check payload size limit to prevent memory exhaustion attacks
            if (decodedBytes.length > config.getMaxPayloadSize()) {
                throw rejectAccounted(SecurityEventCounter.EventType.DECODED_PART_SIZE_EXCEEDED,
                        JWTValidationLogMessages.WARN.DECODED_PART_SIZE_EXCEEDED.format(config.getMaxPayloadSize()),
                        logWarnings, trackSecurityEvents,
                        JWTValidationLogMessages.WARN.DECODED_PART_SIZE_EXCEEDED, config.getMaxPayloadSize());
            }

            // Enforce structural bounds (nesting depth / array size) before handing the bytes to
            // DSL-JSON, so a deeply-nested or oversized-array payload fails closed with a typed
            // exception rather than risking a StackOverflowError during recursive parsing.
            enforceStructuralBounds(decodedBytes, logWarnings, trackSecurityEvents);

            return decodedBytes;
        } catch (IllegalArgumentException e) {
            // The decoder's message names the offending input character; it stays on the cause
            // chain only, never in the WARN or the exception message.
            throw rejectUndecodablePart("Failed to decode Base64Url part: invalid Base64Url encoding",
                    e, logWarnings, trackSecurityEvents);
        }
    }

    /**
     * Rejects a JWT part that cannot be decoded, accounting for it according to the decode mode.
     * <p>
     * Logs {@code WARN.FAILED_TO_DECODE_JWT} without an attached throwable when
     * {@code logWarnings} is set, counts {@code FAILED_TO_DECODE_JWT} when
     * {@code trackSecurityEvents} is set, and throws. The message must not carry raw part
     * bytes; the underlying failure is kept on the cause chain only.
     *
     * @param message              the exception message, free of raw part bytes
     * @param cause                the underlying decoder failure, or {@code null} if there is none
     * @param logWarnings          whether the rejection is logged
     * @param trackSecurityEvents  whether the rejection is counted
     * @return never returns normally; declared so callers can write {@code throw rejectUndecodablePart(...)}
     * @throws TokenValidationException always, with event type {@code FAILED_TO_DECODE_JWT}
     */
    private TokenValidationException rejectUndecodablePart(String message, @Nullable Throwable cause,
            boolean logWarnings, boolean trackSecurityEvents) {
        if (logWarnings) {
            LOGGER.warn(JWTValidationLogMessages.WARN.FAILED_TO_DECODE_JWT);
        }
        if (trackSecurityEvents) {
            securityEventCounter.increment(SecurityEventCounter.EventType.FAILED_TO_DECODE_JWT);
        }
        if (cause == null) {
            throw new TokenValidationException(SecurityEventCounter.EventType.FAILED_TO_DECODE_JWT, message);
        }
        throw new TokenValidationException(SecurityEventCounter.EventType.FAILED_TO_DECODE_JWT, message, cause);
    }

    /**
     * Scans decoded JSON bytes and enforces the configured nesting-depth and array-size bounds.
     * <p>
     * The scan is a single linear pass that tracks string state (so braces/brackets inside string
     * literals are ignored) and, for every object/array frame, its depth and — for arrays — its
     * element count. Exceeding {@link ParserConfig#getMaxNestingDepth()} or
     * {@link ParserConfig#getMaxArraySize()} fails closed with a typed
     * {@link TokenValidationException}.
     *
     * @param jsonBytes            the decoded (UTF-8) JSON bytes of a single JWT part
     * @param logWarnings          whether a bounds violation is logged
     * @param trackSecurityEvents  whether a bounds violation is counted
     */
    @SuppressWarnings({"java:S3776", "java:S6916"}) // single-pass state-tracking scan is a deliberate security-critical
    // DoS bound check; splitting the switch cases or the method risks introducing a subtle parsing bug
    private void enforceStructuralBounds(byte[] jsonBytes, boolean logWarnings, boolean trackSecurityEvents) {
        final int maxDepth = config.getMaxNestingDepth();
        final int maxArraySize = config.getMaxArraySize();
        final boolean[] frameIsArray = new boolean[maxDepth + 1];
        final int[] frameElementCount = new int[maxDepth + 1];
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (byte rawByte : jsonBytes) {
            char c = (char) rawByte;
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> inString = true;
                case '{', '[' -> {
                    depth++;
                    if (depth > maxDepth) {
                        throw rejectStructuralBounds("nesting depth exceeds the configured maximum of " + maxDepth,
                                logWarnings, trackSecurityEvents);
                    }
                    frameIsArray[depth] = c == '[';
                    frameElementCount[depth] = 0;
                }
                case '}', ']' -> {
                    if (depth > 0) {
                        depth--;
                    }
                }
                case ',' -> {
                    if (depth > 0 && frameIsArray[depth]) {
                        frameElementCount[depth]++;
                        if (frameElementCount[depth] >= maxArraySize) {
                            throw rejectStructuralBounds("array size exceeds the configured maximum of " + maxArraySize,
                                    logWarnings, trackSecurityEvents);
                        }
                    }
                }
                default -> {
                    // structural scan ignores all other characters
                }
            }
        }
    }

    /**
     * Rejects a JWT part that violates the configured JSON structural bounds, accounting for it
     * according to the decode mode.
     *
     * @param reason               a human-readable description of the violated bound
     * @param logWarnings          whether the rejection is logged
     * @param trackSecurityEvents  whether the rejection is counted
     * @return never returns normally; declared so callers can write {@code throw rejectStructuralBounds(...)}
     * @throws TokenValidationException always, with event type {@code JSON_STRUCTURE_BOUNDS_EXCEEDED}
     */
    private TokenValidationException rejectStructuralBounds(String reason, boolean logWarnings,
            boolean trackSecurityEvents) {
        throw rejectAccounted(SecurityEventCounter.EventType.JSON_STRUCTURE_BOUNDS_EXCEEDED,
                "JSON structure bounds exceeded: " + reason,
                logWarnings, trackSecurityEvents,
                JWTValidationLogMessages.WARN.JSON_STRUCTURE_BOUNDS_EXCEEDED, reason);
    }

}
