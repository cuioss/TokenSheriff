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
package de.cuioss.sheriff.token.quarkus.producer;

import de.cuioss.sheriff.token.commons.error.ErrorTextSanitizer;
import de.cuioss.sheriff.token.commons.http.BearerCredentials;
import de.cuioss.sheriff.token.commons.metrics.MetricIdentifier;
import de.cuioss.sheriff.token.quarkus.annotation.BearerToken;
import de.cuioss.sheriff.token.quarkus.annotation.ServletObjectsResolver;
import de.cuioss.sheriff.token.quarkus.config.JwtPropertyKeys;
import de.cuioss.sheriff.token.quarkus.servlet.HttpRequestResolver;
import de.cuioss.sheriff.token.validation.TokenValidator;
import de.cuioss.sheriff.token.validation.domain.context.AccessTokenRequest;
import de.cuioss.sheriff.token.validation.domain.token.AccessTokenContent;
import de.cuioss.sheriff.token.validation.exception.TokenValidationException;
import de.cuioss.tools.logging.CuiLogger;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.CreationException;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static de.cuioss.sheriff.token.quarkus.TokenSheriffQuarkusLogMessages.WARN.*;

/**
 * CDI producer for extracting and validating bearer tokens from HTTP requests.
 * <p>
 * This producer extracts the token using HttpRequestResolver, validates it using the configured
 * TokenValidator, and checks for required scopes, roles, and groups. The token source is set by
 * {@link JwtPropertyKeys.TOKEN#HEADER} ({@code sheriff.token.token.header}):
 * <ul>
 *   <li>{@code Authorization} (default) - the token is read from the HTTP {@code Authorization}
 *       header's {@code Bearer} credentials</li>
 *   <li>{@code Cookie} - the {@code Authorization} header stays the primary source and is tried first;
 *       the HTTP cookie named by {@link JwtPropertyKeys.TOKEN#COOKIE_NAME}
 *       ({@code sheriff.token.token.cookie-name}, default {@code Bearer}) is the fallback, read only
 *       when the {@code Authorization} header carries no {@code Bearer} credentials</li>
 * </ul>
 * <p>
 * The {@code Authorization} value is read by {@link BearerCredentials}, which applies the RFC rules: the
 * scheme name matches case-insensitively ({@code Bearer}, {@code bearer} and {@code BEARER} all name it),
 * leading and trailing whitespace of the field value is ignored, and the scheme must be followed by one
 * or more spaces. A value naming another scheme, the bare scheme {@code "Bearer"} (a server or proxy may
 * strip the trailing space of {@code "Bearer "}), or the scheme followed by a tab carries no
 * {@code Bearer} credentials and is treated as no token at all (with {@code Cookie} configured, the cookie
 * fallback applies). When the scheme is presented but the token is empty, whitespace-only, or holds a
 * space, a tab or a control character, the result is {@link BearerTokenStatus#INVALID_REQUEST} (HTTP 400)
 * and the cookie is not consulted.
 * <p>
 * The producer provides both service methods that return {@link BearerTokenResult} with comprehensive
 * validation information and a CDI producer method that returns {@link BearerTokenResult} directly for injection.
 * <p>
 * CDI injection usage example:
 * <pre>{@code
 * @RequestScoped
 * @Path("/api")
 * public class MyResource {
 *
 *     @Inject
 *     @BearerToken(requiredScopes = {"read", "write"})
 *     BearerTokenResult tokenResult;
 *
 *     @GET
 *     public Response getData() {
 *         if (tokenResult.isNotSuccessfullyAuthorized()) {
 *             return tokenResult.createErrorResponse();
 *         }
 *         AccessTokenContent token = tokenResult.getAccessTokenContent()
 *                 .orElseThrow(() -> new IllegalStateException("Token content missing after successful authorization"));
 *         // Use validated token - getSubject() returns Optional&lt;String&gt;
 *         return Response.ok(token.getSubject().orElse("unknown")).build();
 *     }
 * }
 * }</pre>
 * <p>
 * <strong>Important:</strong> For Application-Scoped beans, use {@link jakarta.inject.Provider} since
 * BearerTokenResult is RequestScoped. The preferred way is for the containing class to be RequestScoped
 * as well, then you can use constructor injection:
 * <pre>{@code
 * @RequestScoped
 * public class MyService {
 *     private final BearerTokenResult tokenResult;
 *
 *     @Inject
 *     public MyService(@BearerToken(requiredRoles = {"admin"}) BearerTokenResult tokenResult) {
 *         this.tokenResult = tokenResult;
 *     }
 * }
 * }</pre>
 * <p>
 * For direct service usage, use the CDI producer method with annotations:
 * <pre>{@code
 * @Inject
 * @BearerToken(requiredScopes = {"read"}, requiredRoles = {"user"})
 * BearerTokenResult tokenResult;
 *
 * public Response someMethod() {
 *     if (tokenResult.isNotSuccessfullyAuthorized()) {
 *         return tokenResult.createErrorResponse();
 *     }
 *     AccessTokenContent content = tokenResult.getAccessTokenContent()
 *             .orElseThrow(() -> new IllegalStateException("Token content missing after successful authorization"));
 *     // Use validated token
 *     return Response.ok().build();
 * }
 * }</pre>
 *
 * @since 1.0
 * @author Oliver Wolff
 */
@ApplicationScoped
public class BearerTokenProducer {

    private static final CuiLogger LOGGER = new CuiLogger(BearerTokenProducer.class);
    static final String AUTHORIZATION_HEADER_VALUE = "Authorization";
    static final String COOKIE_HEADER_VALUE = "Cookie";
    static final String EMPTY_TOKEN_MESSAGE = "Bearer token is empty";
    static final String MALFORMED_TOKEN_MESSAGE = "Bearer token holds a space or control character";

    private final TokenValidator tokenValidator;
    private final HttpRequestResolver servletObjectsResolver;
    private final Provider<JsonWebToken> jsonWebTokenProvider;
    private final String tokenHeader;
    private final String tokenCookieName;

    @Inject
    public BearerTokenProducer(TokenValidator tokenValidator,
            @ServletObjectsResolver HttpRequestResolver servletObjectsResolver,
            Provider<JsonWebToken> jsonWebTokenProvider,
            @ConfigProperty(name = JwtPropertyKeys.TOKEN.HEADER, defaultValue = AUTHORIZATION_HEADER_VALUE) String tokenHeader,
            @ConfigProperty(name = JwtPropertyKeys.TOKEN.COOKIE_NAME, defaultValue = "Bearer") String tokenCookieName) {
        // Property values may carry accidental surrounding whitespace — trim before validating
        String trimmedHeader = tokenHeader.trim();
        if (!AUTHORIZATION_HEADER_VALUE.equalsIgnoreCase(trimmedHeader)
                && !COOKIE_HEADER_VALUE.equalsIgnoreCase(trimmedHeader)) {
            throw new IllegalStateException(
                    "Invalid value '%s' for property '%s'. Supported values are '%s' and '%s'."
                            .formatted(tokenHeader, JwtPropertyKeys.TOKEN.HEADER,
                                    AUTHORIZATION_HEADER_VALUE, COOKIE_HEADER_VALUE));
        }
        this.tokenValidator = tokenValidator;
        this.servletObjectsResolver = servletObjectsResolver;
        this.jsonWebTokenProvider = jsonWebTokenProvider;
        this.tokenHeader = trimmedHeader;
        this.tokenCookieName = tokenCookieName.trim();
    }

    /**
     * Gets the current request's AccessTokenContent from the configured token source.
     * This method handles tokens without specific requirements.
     *
     * @return Optional containing validated AccessTokenContent, or empty if validation fails
     */
    private Optional<AccessTokenContent> getAccessTokenContent() {
        return getBearerTokenResult(Set.of(), Set.of(), Set.of()).getAccessTokenContent();
    }

    /**
     * Gets comprehensive bearer token validation result with detailed status information.
     * <p>
     * The three requirement sets are copied on entry, so the returned result never shares a set with the
     * caller: a later change to a caller's set, such as a {@code clear()}, does not change the result.
     * </p>
     *
     * @param requiredScopes Required scopes for the token; must not be {@code null} or hold {@code null}
     * @param requiredRoles Required roles for the token; must not be {@code null} or hold {@code null}
     * @param requiredGroups Required groups for the token; must not be {@code null} or hold {@code null}
     * @return BearerTokenResult containing detailed validation information
     */
    @Timed(value = MetricIdentifier.BEARERTOKEN.VALIDATION, description = "Bearer token validation duration")
    public BearerTokenResult getBearerTokenResult(
            Set<String> requiredScopes, Set<String> requiredRoles, Set<String> requiredGroups) {
        return validate(Set.copyOf(requiredScopes), Set.copyOf(requiredRoles), Set.copyOf(requiredGroups));
    }

    private BearerTokenResult validate(
            Set<String> requiredScopes, Set<String> requiredRoles, Set<String> requiredGroups) {

        LOGGER.debug("Validating bearer token with required scopes: %s, roles: %s, groups: %s",
                requiredScopes, requiredRoles, requiredGroups);

        // Resolve headers once and reuse for both extraction and validation request
        Map<String, List<String>> headerMap = servletObjectsResolver.resolveHeaderMap();

        BearerCredentials.Result credentials = BearerCredentials.extract(headerMap);
        if (credentials.state() == BearerCredentials.State.MALFORMED) {
            // RFC 6750 §3.1: presented but unusable credentials are a malformed request (400); the
            // cookie is not consulted, because the client did present Authorization credentials
            String reason = credentials.isTokenEmpty() ? EMPTY_TOKEN_MESSAGE : MALFORMED_TOKEN_MESSAGE;
            LOGGER.debug("%s - invalid request per RFC 6750", reason);
            return BearerTokenResult.invalidRequest(reason, requiredScopes, requiredRoles, requiredGroups);
        }

        Optional<String> tokenResult = credentials.token();

        // When token.header=Cookie, fall back to cookie extraction if no Authorization token
        if (tokenResult.isEmpty() && COOKIE_HEADER_VALUE.equalsIgnoreCase(tokenHeader)) {
            tokenResult = extractTokenFromCookieHeader(headerMap);
        }

        if (tokenResult.isEmpty()) {
            if (COOKIE_HEADER_VALUE.equalsIgnoreCase(tokenHeader)) {
                LOGGER.debug("Bearer token not found in Authorization header or '%s' cookie", tokenCookieName);
            } else {
                LOGGER.debug("Bearer token missing or invalid in Authorization header");
            }
            return BearerTokenResult.noTokenGiven(requiredScopes, requiredRoles, requiredGroups);
        }

        // Both sources yield a non-blank token: BearerCredentials refuses an empty one as MALFORMED
        // and the cookie lookup treats an empty or blank value as no cookie
        String bearerToken = tokenResult.get();

        try {
            // Pass HTTP headers and request context through for DPoP htu/htm validation (RFC 9449)
            RequestContext requestContext = resolveRequestContext();
            AccessTokenContent tokenContent = tokenValidator.createAccessToken(
                    new AccessTokenRequest(bearerToken, headerMap, requestContext.uri(), requestContext.method()));

            // Determine missing scopes, roles, and groups
            Set<String> missingScopes = tokenContent.determineMissingScopes(requiredScopes);
            Set<String> missingRoles = tokenContent.determineMissingRoles(requiredRoles);
            Set<String> missingGroups = tokenContent.determineMissingGroups(requiredGroups);

            if (missingScopes.isEmpty() && missingRoles.isEmpty() && missingGroups.isEmpty()) {
                LOGGER.debug("Bearer token validation successful");
                return BearerTokenResult.builder()
                        .status(BearerTokenStatus.FULLY_VERIFIED)
                        .accessTokenContent(tokenContent)
                        .build();
            } else {
                LOGGER.warn(BEARER_TOKEN_REQUIREMENTS_NOT_MET_DETAILED, missingScopes, missingRoles, missingGroups);
                return BearerTokenResult.builder()
                        .status(BearerTokenStatus.CONSTRAINT_VIOLATION)
                        .requiredScopes(requiredScopes)
                        .requiredRoles(requiredRoles)
                        .requiredGroups(requiredGroups)
                        .missingScopes(missingScopes)
                        .missingRoles(missingRoles)
                        .missingGroups(missingGroups)
                        .build();
            }
        } catch (TokenValidationException e) {
            // The message of a validation failure can quote token-header text (a kid, an alg) that the
            // pipeline interpolates into it verbatim, so it is escaped and bounded before it is logged and
            // the exception object, whose stack trace would print the same message raw, is not attached
            // (CWE-117, ADR-0022). The exception stays in the returned result for callers.
            LOGGER.warn(BEARER_TOKEN_VALIDATION_FAILED, ErrorTextSanitizer.sanitize(e.getMessage()), e.getEventType());
            return BearerTokenResult.parsingError(e, requiredScopes, requiredRoles, requiredGroups);
        }
    }


    /**
     * Holds the resolved HTTP request URI and method for DPoP htu/htm validation.
     */
    private record RequestContext(String uri, String method) {
    }

    /**
     * Resolves the HTTP request URI and method for DPoP htu/htm validation.
     * Both values default to {@code null} if resolution fails.
     *
     * @return a RequestContext with the resolved URI and method
     */
    private RequestContext resolveRequestContext() {
        try {
            return new RequestContext(
                    servletObjectsResolver.resolveRequestUri(),
                    servletObjectsResolver.resolveRequestMethod()
            );
        }
        catch (IllegalStateException | UnsupportedOperationException | CreationException e) {
            LOGGER.debug("Could not resolve request URI/method for DPoP htu/htm validation: %s", e.getMessage());
            return new RequestContext(null, null);
        }
    }

    /**
     * Extracts a JWT token from the HTTP Cookie header.
     * <p>
     * Parses the Cookie header value according to RFC 6265 and looks for a cookie
     * matching the configured {@link #tokenCookieName}. Cookie values may optionally
     * be enclosed in double quotes per RFC 6265 Section 4.1.1.
     * </p>
     *
     * @param headerMap the resolved HTTP header map (keys are lowercase)
     * @return Optional containing the token value, or empty if cookie not found
     */
    Optional<String> extractTokenFromCookieHeader(Map<String, List<String>> headerMap) {
        List<String> cookieHeaders = headerMap.get("cookie");
        if (cookieHeaders == null || cookieHeaders.isEmpty()) {
            LOGGER.debug("Cookie header not found in headerMap");
            return Optional.empty();
        }

        // Cookie header format: "name1=value1; name2=value2"
        for (String cookieHeader : cookieHeaders) {
            if (cookieHeader == null) {
                continue;
            }
            Optional<String> result = findCookieValue(cookieHeader);
            if (result.isPresent()) {
                return result;
            }
        }

        LOGGER.debug("Cookie '%s' not found in Cookie header", tokenCookieName);
        return Optional.empty();
    }

    private Optional<String> findCookieValue(String cookieHeader) {
        String[] cookies = cookieHeader.split(";");
        for (String cookie : cookies) {
            String trimmed = cookie.trim();
            int eqIndex = trimmed.indexOf('=');
            if (eqIndex > 0 && tokenCookieName.equals(trimmed.substring(0, eqIndex).trim())) {
                String value = trimmed.substring(eqIndex + 1).trim();
                // Remove optional surrounding double quotes per RFC 6265
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                // Blank, not just empty: removing the quotes can leave whitespace only (Bearer="   ")
                if (value.isBlank()) {
                    LOGGER.debug("Cookie '%s' found but value is empty", tokenCookieName);
                    return Optional.empty();
                }
                LOGGER.debug("Token extracted from cookie '%s'", tokenCookieName);
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    /**
     * Produces the current request's BearerTokenResult as a CDI bean.
     * <p>
     * This producer method provides comprehensive bearer token validation information
     * including detailed status, error information, and the validated token content.
     * This method extracts the bearer token from the configured token source (the HTTP
     * {@code Authorization} header, with the cookie as fallback when {@code sheriff.token.token.header}
     * is {@code Cookie}) and validates it using the configured TokenValidator.
     * <p>
     * The producer method is @Dependent scoped, which means it will be created fresh
     * for each injection point. Unlike the AccessTokenContent producer, this method
     * always returns a valid BearerTokenResult object containing detailed information
     * about the validation outcome.
     * <p>
     * Usage example:
     * <pre>{@code
     * @Inject
     * @BearerToken(requiredScopes = {"read", "write"})
     * BearerTokenResult tokenResult;
     *
     * public void someMethod() {
     *     switch (tokenResult.getStatus()) {
     *         case FULLY_VERIFIED:
     *             AccessTokenContent token = tokenResult.getAccessTokenContent().get();
     *             // Use validated token
     *             break;
     *         case PARSING_ERROR:
     *             // Handle parsing errors with detailed information
     *             EventType eventType = tokenResult.getEventType().get();
     *             String message = tokenResult.getMessage().get();
     *             break;
     *         default:
     *             // Handle other validation failures
     *     }
     * }
     * }</pre>
     *
     * @param injectionPoint the CDI injection point containing the BearerToken annotation
     * @return the BearerTokenResult containing detailed validation information
     */
   
    @Produces
    @BearerToken
    public BearerTokenResult produceBearerTokenResult(InjectionPoint injectionPoint) {
        BearerToken annotation = injectionPoint.getAnnotated().getAnnotation(BearerToken.class);

        Set<String> requiredScopes = annotation != null ? Set.copyOf(List.of(annotation.requiredScopes())) : Set.of();
        Set<String> requiredRoles = annotation != null ? Set.copyOf(List.of(annotation.requiredRoles())) : Set.of();
        Set<String> requiredGroups = annotation != null ? Set.copyOf(List.of(annotation.requiredGroups())) : Set.of();

        return getBearerTokenResult(requiredScopes, requiredRoles, requiredGroups);
    }

    /**
     * Produces the current request's {@link JsonWebToken} for standard MP-JWT injection.
     * <p>
     * This enables the standard MicroProfile JWT Auth injection pattern:
     * <pre>{@code
     * @Inject JsonWebToken callerPrincipal;
     * }</pre>
     * <p>
     * The produced token is the validated {@link AccessTokenContent} (which implements
     * {@link JsonWebToken}) from the current HTTP request. If no valid token is present,
     * returns an empty {@link JsonWebToken} where all method calls return null/empty
     * (per MP-JWT spec: "an empty JsonWebToken is injected").
     *
     * @return the validated JsonWebToken, or an empty token if not authenticated
     */
    @Produces
    @RequestScoped
    public JsonWebToken produceJsonWebToken() {
        return getAccessTokenContent()
                .<JsonWebToken>map(JsonWebTokenAdapter::new)
                .orElse(EmptyJsonWebToken.INSTANCE);
    }

    /**
     * Produces the current request's {@link Principal} for standard Jakarta Security injection.
     * <p>
     * This enables the standard Jakarta Security injection pattern:
     * <pre>{@code
     * @Inject Principal principal;
     * }</pre>
     * <p>
     * The {@link JsonWebToken} is resolved through the CDI container (rather than by
     * calling {@link #produceJsonWebToken()} directly) so that the container caches the
     * {@code @RequestScoped} instance and the token is validated at most once per request,
     * even when both {@code Principal} and {@code JsonWebToken} are injected.
     *
     * @return the validated Principal (which is the JsonWebToken), or an empty principal
     */
    @Produces
    @RequestScoped
    public Principal producePrincipal() {
        return jsonWebTokenProvider.get();
    }
}