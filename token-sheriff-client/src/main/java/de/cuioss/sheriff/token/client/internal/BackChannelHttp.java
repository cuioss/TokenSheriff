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
import de.cuioss.http.security.config.SecurityConfiguration;
import de.cuioss.http.security.core.HttpSecurityValidator;
import de.cuioss.http.security.exceptions.UrlSecurityException;
import de.cuioss.http.security.monitoring.SecurityEventCounter;
import de.cuioss.http.security.pipeline.PipelineFactory;
import de.cuioss.sheriff.token.client.config.ClientConfiguration;
import de.cuioss.sheriff.token.commons.error.TransportException;
import de.cuioss.sheriff.token.commons.transport.BoundedBodyHandler;
import de.cuioss.sheriff.token.commons.transport.EgressPolicy;
import de.cuioss.tools.logging.CuiLogger;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.ProtocolException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * Shared back-channel transport control for the OIDC/OAuth endpoint clients.
 * <p>
 * The five endpoint clients (token, PAR, userinfo, revocation, discovery) each issue their own
 * outbound {@code cui-http} calls to URLs the authorization server <em>advertised</em> in its
 * discovery document. Discovery data is only transport-authenticated — it is not fully trusted — so
 * an AS (or a mix-up attacker who controls the discovery response) could point a credential-bearing
 * back-channel {@code POST} at an unintended target. This helper centralises the egress control the
 * clients apply before each fetch, so the same defence is enforced uniformly rather than copied,
 * inconsistently, five times:
 * <ul>
 *   <li><strong>Egress / SSRF path control (H9):</strong> the advertised endpoint URL is run through the
 *       existing {@code de.cuioss.http.security} URL-path validation pipeline before use, so a
 *       traversal / injection / encoding-evasion / malformed advertised path is rejected rather than
 *       fetched.</li>
 *   <li><strong>Host-address egress policy:</strong> before every fetch the client's own
 *       {@link EgressPolicy} ({@link ClientConfiguration#getEgressPolicy()}, a separate instance from the
 *       validation transport's) resolves the host of the URL <em>actually contacted</em> — after the
 *       back-channel origin rewrite below — and refuses it when any resolved address is loopback,
 *       link-local (including the cloud-metadata range), site-local, unique-local, carrier-grade NAT,
 *       wildcard, broadcast or multicast. The policy is secure by default; its only relaxations are the
 *       two named configuration inputs {@link ClientConfiguration#allowLoopbackEgress} and
 *       {@link ClientConfiguration#getAllowedEgressHosts()}, which apply to configured endpoints and to a
 *       back-channel origin exactly as to advertised ones. A refusal is the policy's typed
 *       {@link TransportException} SSRF-blocked branch. The resolution happens at the check, and the
 *       HTTP client resolves the host again when it connects, so a DNS answer that changes in between
 *       is not seen — the DNS-rebinding limit {@link EgressPolicy} states.</li>
 *   <li><strong>Scheme / TLS control:</strong> a non-TLS ({@code http://}) endpoint is refused unless
 *       {@link ClientConfiguration#allowInsecureHttp} is set, via the {@link HttpHandler} builder's
 *       own TLS enforcement.</li>
 *   <li><strong>Per-client TLS trust:</strong> the {@link ClientConfiguration#getSslContext() configured
 *       SSLContext}, when present, is applied to every TLS endpoint handler this helper produces — so all
 *       five endpoint clients sharing one {@code ClientConfiguration} trust the same authorization
 *       server, without a process-global {@code javax.net.ssl.trustStore} override. When absent, the
 *       cui-http / JVM default truststore is used.</li>
 *   <li><strong>Hostname verification:</strong> {@link ClientConfiguration#verifyHostname} is forwarded
 *       onto every TLS endpoint handler this helper produces. It defaults to {@code true}; setting it to
 *       {@code false} relaxes hostname matching only, leaving chain trust, expiry, and algorithm
 *       constraints enforced. It is mutually exclusive with the per-client TLS trust above — the two are
 *       rejected together at {@link ClientConfiguration} build time, so that guard, not the
 *       {@code catch} / {@link TransportException} path below, is what refuses the combination.</li>
 *   <li><strong>Back-channel origin:</strong> when {@link ClientConfiguration#backChannelOrigin} is set,
 *       an advertised endpoint whose origin (lower-cased scheme, lower-cased host, effective port) equals
 *       the issuer origin is contacted at the back-channel origin, with its raw path and raw query kept
 *       verbatim; an endpoint on any other origin is contacted unchanged. Both origins are read from
 *       configuration only, so neither the request nor the advertised data can steer a call to a third
 *       origin — the rewrite adds no SSRF primitive. The egress path validation runs on the advertised
 *       URL before the rewrite, and the host-address policy, scheme, TLS-trust and hostname controls
 *       above are applied to the rewritten target, i.e. to the host actually contacted. The returned handler's URI is the
 *       URL actually contacted, and it is what a DPoP proof names as {@code htu} (RFC 9449 §4.2).</li>
 *   <li><strong>Consistent transport typing (M9):</strong> a malformed or non-TLS endpoint surfaces as
 *       the declared {@link TransportException}, never a raw {@link IllegalArgumentException} leaking
 *       from {@code HttpHandler.build()}.</li>
 *   <li><strong>Client reuse (L16):</strong> a single {@link HttpClient} is built lazily and reused for
 *       every request issued through this helper instance, instead of a fresh client per request.</li>
 *   <li><strong>Configurable timeouts (L15):</strong> connect / read timeouts are read from
 *       {@link ClientConfiguration}, not hardcoded per client.</li>
 *   <li><strong>Bounded body reads (M7):</strong> {@link #bodyHandler()} enforces the ceiling the
 *       instance was constructed with during the read via the commons {@link BoundedBodyHandler}: a response advertising a
 *       {@code Content-Length} above the ceiling fails before any byte is read, and a body that crosses
 *       the ceiling while streaming fails at the crossing. The ceiling's provenance travels with the
 *       failure: each construction site supplies a {@code boundOrigin} descriptor naming the knob that
 *       set the bound and whether it is settable, and the over-limit message reports it.</li>
 *   <li><strong>Malformed response framing:</strong> {@link #send(HttpClient, HttpRequest, String)} is
 *       the send of the discovery, PAR, userinfo and revocation clients; the token endpoint client, which
 *       wraps the body handler to observe the status line, maps the same exception in its own send. The
 *       JDK HTTP/1.1 client parses a response's {@code Content-Length} itself — after the body handler
 *       returned, or for a {@code 204} or other no-body reply before any body handler runs — and refuses
 *       a value that is not a number with the parser's {@link NumberFormatException}, which carries the
 *       raw header value: the {@link HttpClient#sendAsync} future completes with it, {@link HttpClient#send}
 *       wraps it in an {@link IllegalArgumentException}, and since JDK 27 it sits under a
 *       {@link ProtocolException} in the cause chain, see {@link #isMalformedFraming(Throwable)}. Both sends
 *       wait on {@link HttpClient#sendAsync} through {@link #exchange}, which unwraps the failure of the
 *       exchange through {@link CompletionException} and {@link ExecutionException} wrappers alike, nested
 *       ones included, for at most {@code MAX_CAUSE_DEPTH} levels: a malformed-framing failure of the
 *       exchange is mapped to a {@link TransportException} with the failure context and the fixed text
 *       {@value #MALFORMED_RESPONSE}, the JDK's exception neither chained nor echoed because its message
 *       is remote content; an {@link IOException} or {@link Error} failure of the exchange is rethrown as
 *       the client reported it; a cancelled wait, and a future failed with a wrapped
 *       {@link CancellationException}, is an {@link IOException} with a fixed text; any other failure of
 *       the exchange, checked or runtime, an {@link IllegalArgumentException} of another cause included,
 *       is an {@link IOException} with a fixed text and no chained cause, so each client classifies it
 *       through its {@link IOException} handling. The wrapped-cancellation, nested-wrapper, checked-cause
 *       and runtime-failure mappings are defensive: no JDK path producing any of them is known from
 *       reading the client. An {@link IllegalArgumentException} thrown synchronously, for a request the
 *       client refuses before sending, is a request-side fault and reaches the caller as thrown.</li>
 *   <li><strong>Wait bounds:</strong> cui-http 3.2's {@link HttpHandler} sets two timeouts and no
 *       other: the connect timeout on the {@link HttpClient} it builds
 *       ({@code HttpClient.Builder.connectTimeout}), and the read timeout as the request timeout in
 *       {@link HttpHandler#requestBuilder()} ({@code HttpRequest.Builder.timeout}). The request timeout
 *       bounds the wait until the response headers arrive. Whether it also bounds the body read that
 *       follows depends on the JDK: on JDK 21 and 25 it was observed not to, on JDK 27 it was observed to,
 *       ending the call with the request-timeout failure or with the JDK's own failure for the body it cut
 *       short. The code relies on the size bound of the body
 *       read, not on a time bound: the ceiling this instance was constructed with — the JWT payload
 *       ceiling for the token, PAR, userinfo and revocation clients,
 *       {@link ClientConfiguration#getDiscoveryDocumentMaxSize()} for discovery — see
 *       {@link #exchange}.</li>
 * </ul>
 *
 * @since 1.0
 * @author Oliver Wolff
 */
public final class BackChannelHttp {

    private static final CuiLogger LOGGER = new CuiLogger(BackChannelHttp.class);

    /** Fixed refusal reason of the host-address egress policy; carries no part of the refused URL. */
    private static final String HOST_EGRESS_REFUSAL =
            "SSRF egress blocked: the host of the contacted URL is not permitted by the host-address egress policy";

    /**
     * Fixed reason for a response the JDK HTTP client refused as malformed, such as a
     * {@code Content-Length} that is not a number; carries no part of the response. Public so that an
     * endpoint client with its own send — the token endpoint, which tells a failure after a success
     * status apart — reports the same text.
     */
    public static final String MALFORMED_RESPONSE = "the HTTP client refused the response as malformed";

    /**
     * Fixed text of the {@link IOException} that replaces a cancelled wait of {@link #exchange}, and a
     * future failed with a wrapped {@link CancellationException}.
     */
    static final String EXCHANGE_CANCELLED = "the HTTP exchange was cancelled";

    /**
     * Fixed text of the {@link IOException} that replaces every response-side failure of
     * {@link #exchange} that is none of malformed framing, an {@link IOException}, a
     * {@link CancellationException} and an {@link Error} — a runtime or a checked cause alike, and a
     * wrapper chain deeper than the unwrap bound — with no chained cause.
     */
    static final String EXCHANGE_FAILED = "the HTTP exchange failed";

    /**
     * Bound of the cause-chain walk of {@link #isMalformedFraming(Throwable)}, and of the unwrap of nested
     * {@link CompletionException} and {@link ExecutionException} wrappers in {@link #exchange}.
     */
    static final int MAX_CAUSE_DEPTH = 8;

    /**
     * Tells whether {@code failure} is the JDK HTTP client's refusal of a response whose
     * {@code Content-Length} is not a number. The client's {@code Content-Length} parser fails with a
     * {@link NumberFormatException}, and the exchange reports it in one of three forms:
     * <ul>
     *   <li>the {@link NumberFormatException} itself — the failure of the {@link HttpClient#sendAsync}
     *       future up to JDK 26;</li>
     *   <li>an {@link IllegalArgumentException} with a {@link NumberFormatException} in its cause chain —
     *       the form {@link HttpClient#send} throws up to JDK 26;</li>
     *   <li>a {@link ProtocolException} caused by the {@link NumberFormatException}, anywhere in the cause
     *       chain — the form since JDK 27, where the client's {@link ProtocolException} wraps the parser's.</li>
     * </ul>
     * Each carries the raw header value in its message, so a caller that recognises it maps it to a fixed
     * text and never chains or echoes it. An {@link IllegalArgumentException} without a
     * {@link NumberFormatException} in its cause chain, and a {@link ProtocolException} of another cause,
     * are different failures and are not matched. The cause-chain walk is bounded, so a cyclic chain cannot
     * loop.
     *
     * @param failure the failure of an exchange, as {@link #exchange} passes it: the cause that completed
     *                the {@link HttpClient#sendAsync} future, unwrapped from its {@link ExecutionException}
     *                and from any nested {@link CompletionException} or {@link ExecutionException}, within
     *                the bound of {@code MAX_CAUSE_DEPTH} levels; may be {@code null}
     * @return {@code true} if it is the malformed {@code Content-Length} refusal
     */
    public static boolean isMalformedFraming(@Nullable Throwable failure) {
        if (failure instanceof NumberFormatException) {
            return true;
        }
        boolean illegalArgument = failure instanceof IllegalArgumentException;
        Throwable current = failure;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            Throwable cause = current.getCause();
            if (cause instanceof NumberFormatException && (illegalArgument || current instanceof ProtocolException)) {
                return true;
            }
            current = cause;
        }
        return false;
    }

    /**
     * Bound origin for the back-channel clients (token, PAR, userinfo, revocation) that build their
     * own {@code ParserConfig} internally: their payload ceiling is deliberately fixed on that path
     * and is <em>not</em> reachable from {@link ClientConfiguration} nor from the
     * {@code sheriff.token.parser.max-payload-size} property, which feeds a different object graph.
     * The message must say so rather than let a reader chase a setting that has no effect here.
     * <p>
     * This is a shared descriptor, not a default: {@link #BackChannelHttp(ClientConfiguration, int, String)}
     * still requires the argument, so a new endpoint client cannot silently omit its origin.
     */
    public static final String FIXED_PARSER_CONFIG_ORIGIN =
            "ParserConfig.maxPayloadSize; fixed on this path, not settable via ClientConfiguration";

    private final ClientConfiguration configuration;
    private final HttpSecurityValidator egressValidator;
    private final EgressPolicy hostEgressPolicy;
    private final int maxContentSize;
    private final String boundOrigin;
    private final ConcurrentMap<String, HttpClient> sharedClients = new ConcurrentHashMap<>();
    private final @Nullable String issuerOriginKey;
    private final @Nullable URI backChannelOrigin;

    /**
     * @param configuration  the client configuration carrying the TLS policy, timeout knobs and the
     *                       optional back-channel origin; must not be {@code null}
     * @param maxContentSize the maximum response body size, in bytes, enforced during read
     * @param boundOrigin    the human-readable provenance of {@code maxContentSize} — the knob that set
     *                       it, and whether it is settable; reported in the over-limit failure message
     */
    public BackChannelHttp(ClientConfiguration configuration, int maxContentSize, String boundOrigin) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        this.maxContentSize = maxContentSize;
        this.boundOrigin = Objects.requireNonNull(boundOrigin, "boundOrigin must not be null");
        this.egressValidator = PipelineFactory.createUrlPathPipeline(
                SecurityConfiguration.defaults(), new SecurityEventCounter());
        this.hostEgressPolicy = configuration.getEgressPolicy();
        this.issuerOriginKey = originKey(URI.create(configuration.getIssuer()));
        String configuredOrigin = configuration.getBackChannelOrigin();
        this.backChannelOrigin = configuredOrigin == null ? null : URI.create(configuredOrigin);
    }

    /**
     * Applies the egress / SSRF and scheme controls to an advertised endpoint URL, maps it onto the
     * configured back-channel origin when it shares the issuer origin, and returns a per-endpoint
     * {@link HttpHandler} for the URL actually contacted, configured with the client's timeout and TLS
     * policy.
     * <p>
     * The handler's URI ({@code getUri()}) is the URL actually contacted. Callers that attach
     * a DPoP proof use it as the proof's {@code htu}, so the proof names the target URI of the request
     * it travels with (RFC 9449 §4.2) rather than the advertised URL.
     *
     * @param endpointUrl    the absolute, discovery-advertised endpoint URL to fetch
     * @param failureContext a short, caller-safe prefix identifying the endpoint for exception messages
     *                       (for example {@code "token endpoint request failed"})
     * @return a configured {@link HttpHandler} for the target URL — {@code endpointUrl} itself, or its
     *         back-channel-origin rewrite
     * @throws TransportException if the URL is malformed, fails the egress security validation, the
     *                            contacted host resolves to an address the host-address egress policy
     *                            refuses, or the target is a non-TLS endpoint while cleartext is not
     *                            permitted
     */
    public HttpHandler validatedHandler(String endpointUrl, String failureContext) {
        Objects.requireNonNull(endpointUrl, "endpointUrl must not be null");
        URI endpointUri = applyEgressControl(endpointUrl, failureContext);
        String targetUrl = targetUrl(endpointUri, endpointUrl);
        applyHostEgressPolicy(targetUrl, failureContext);
        try {
            HttpHandler.HttpHandlerBuilder builder = HttpHandler.builder()
                    .url(targetUrl)
                    .connectionTimeoutSeconds(configuration.getConnectTimeoutSeconds())
                    .readTimeoutSeconds(configuration.getReadTimeoutSeconds())
                    .allowInsecureHttp(configuration.isAllowInsecureHttp());
            // The TLS knobs apply to TLS endpoints only: a cleartext endpoint (reachable only with
            // allowInsecureHttp) establishes no TLS connection, and cui-http refuses both sslContext(...)
            // and verifyHostname(false) on an http:// URI. One configuration may serve endpoints of both
            // schemes, so the knobs are skipped per endpoint rather than rejected for the configuration.
            if (!isCleartext(targetUrl)) {
                builder.verifyHostname(configuration.isVerifyHostname());
                SSLContext sslContext = configuration.getSslContext();
                if (sslContext != null) {
                    builder.sslContext(sslContext);
                }
            }
            return builder.build();
        } catch (IllegalArgumentException e) {
            // M9: a non-TLS / malformed endpoint must surface as the declared transport failure, not a
            // raw IllegalArgumentException leaking from the handler builder.
            throw new TransportException(failureContext + ": " + e.getMessage(), e);
        }
    }

    private static boolean isCleartext(String endpointUrl) {
        return "http".equalsIgnoreCase(URI.create(endpointUrl).getScheme());
    }

    private URI applyEgressControl(String endpointUrl, String failureContext) {
        URI uri;
        try {
            uri = URI.create(endpointUrl);
        } catch (IllegalArgumentException e) {
            throw new TransportException(failureContext + ": malformed endpoint URL", e);
        }
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            return uri;
        }
        try {
            egressValidator.validate(path);
        } catch (UrlSecurityException e) {
            LOGGER.debug(e, "Egress validation rejected advertised endpoint path: %s", e.getFailureType());
            throw new TransportException(failureContext
                    + ": endpoint URL failed egress security validation (" + e.getFailureType() + ")", e);
        }
        return uri;
    }

    /**
     * Applies the client's host-address {@link EgressPolicy} to the URL actually contacted — after the
     * back-channel origin rewrite, so the check sees the host the request really goes to. A refusal is
     * reported as a {@link TransportException} with a fixed message: the failure context and the refusal
     * reason only. The policy's own exception is not chained and its message is not reused, because it
     * names the whole contacted URL, which can carry userinfo or query secrets and would reach every
     * log line that prints the exception or its cause.
     *
     * @throws TransportException if the contacted URL has no host or resolves to a disallowed address
     */
    private void applyHostEgressPolicy(String targetUrl, String failureContext) {
        try {
            hostEgressPolicy.check(URI.create(targetUrl));
        } catch (TransportException ignored) {
            // Deliberately neither chained nor reused: the policy's message names the whole URL (CWE-532).
            LOGGER.debug("Host-address egress policy refused the contacted endpoint");
            throw new TransportException(failureContext + ": " + HOST_EGRESS_REFUSAL);
        }
    }

    /**
     * Computes the URL actually contacted for an advertised endpoint. When a back-channel origin is
     * configured and the endpoint's origin equals the issuer origin, scheme, host and port are replaced
     * by the back-channel origin while the raw path and raw query are kept verbatim; otherwise the
     * advertised URL is returned unchanged. Both origins come from configuration only, so the advertised
     * URL can select <em>whether</em> it is rewritten, never <em>where</em> to.
     */
    private String targetUrl(URI endpointUri, String endpointUrl) {
        if (backChannelOrigin == null || !Objects.equals(issuerOriginKey, originKey(endpointUri))) {
            return endpointUrl;
        }
        StringBuilder target = new StringBuilder(backChannelOrigin.getScheme())
                .append("://")
                .append(backChannelOrigin.getRawAuthority());
        String rawPath = endpointUri.getRawPath();
        if (rawPath != null) {
            target.append(rawPath);
        }
        String rawQuery = endpointUri.getRawQuery();
        if (rawQuery != null) {
            target.append('?').append(rawQuery);
        }
        return target.toString();
    }

    /**
     * Derives the normalised origin key of a URI: lower-cased scheme, lower-cased host and the effective
     * port, with an absent port mapped to the scheme default ({@code 443} for {@code https}, {@code 80}
     * for {@code http}). Returns {@code null} for a URI without scheme or host, which therefore never
     * matches the issuer origin.
     */
    private static @Nullable String originKey(URI uri) {
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null) {
            return null;
        }
        String normalisedScheme = scheme.toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        if (port == -1) {
            port = switch (normalisedScheme) {
                case "https" -> 443;
                case "http" -> 80;
                default -> -1;
            };
        }
        return normalisedScheme + "://" + host.toLowerCase(Locale.ROOT) + ":" + port;
    }

    /**
     * Returns the shared {@link HttpClient} for this helper instance, building it once (lazily,
     * thread-safely) per endpoint scheme and reusing it for every subsequent request to that scheme.
     * <p>
     * The client is keyed by scheme rather than being a single instance because
     * {@link HttpClient} bakes its TLS material in at construction: cui-http's {@code HttpHandler}
     * installs the {@link SSLContext} and the TLS 1.2/1.3 {@code SSLParameters} pinning only on its
     * HTTPS path. A single cached client seeded by whichever handler happened to be built first
     * would therefore let a cleartext endpoint — reachable only when
     * {@link ClientConfiguration#allowInsecureHttp} is set — seed a client carrying no trust
     * material, which a later {@code https://} endpoint would then silently reuse. That would drop
     * the configured per-client trust anchor and widen validation back to the JVM default CA set,
     * breaking the CLIENT-23 guarantee that the configured {@link SSLContext} applies to
     * <em>every</em> call. Keying by scheme keeps connection pooling while making that
     * cross-scheme bleed structurally impossible.
     *
     * @param handler an already-validated handler whose SSL context and connect timeout seed the client
     * @return the shared, reused {@link HttpClient} for this handler's scheme
     */
    public HttpClient sharedClient(HttpHandler handler) {
        Objects.requireNonNull(handler, "handler must not be null");
        String scheme = handler.getUri().getScheme();
        return sharedClients.computeIfAbsent(
                scheme == null ? "" : scheme.toLowerCase(Locale.ROOT),
                unusedKey -> handler.createHttpClient());
    }

    /**
     * Sends {@code request} through {@code client} with this helper's {@link #bodyHandler() bounded body
     * handler}.
     * <p>
     * The JDK HTTP/1.1 client parses a response's {@code Content-Length} itself — after the body handler
     * returned, or for a {@code 204} or other no-body reply before any body handler runs — so a value that
     * is not a number never reaches the handler's own fixed failure: the JDK refuses the response with an
     * exception carrying the raw header value, recognised by {@link #isMalformedFraming(Throwable)}. This
     * method maps that exception to a
     * {@link TransportException} whose message is {@code failureContext} and {@value #MALFORMED_RESPONSE};
     * the JDK's exception is neither chained nor reused, because its message is remote content (CWE-117,
     * CWE-532).
     * <p>
     * Request-side and response-side faults are told apart by the documented {@link HttpClient#sendAsync}
     * contract, see {@link #exchange}: an {@link IllegalArgumentException} for a request the client refuses
     * is thrown synchronously and reaches the caller as thrown; a malformed-framing failure of the exchange
     * is a malformed response, whether or not the body handler was invoked; an {@link IOException} or
     * {@link Error} failure of the exchange is rethrown as the client reported it; a cancelled wait and a
     * future failed with a wrapped cancellation are an {@link IOException} with a fixed text; any other
     * failure of the exchange, checked or runtime, is an {@link IOException} with the fixed text
     * {@code "the HTTP exchange failed"} and no chained cause. Each of these is decided on the cause
     * unwrapped from nested {@link CompletionException} and {@link ExecutionException} wrappers within the
     * bound of {@code MAX_CAUSE_DEPTH} levels. The wrapped-cancellation, nested-wrapper, checked-cause and
     * runtime-failure mappings are defensive, no JDK path producing any of them being known. The
     * request's timeout, which {@link HttpHandler#requestBuilder()} sets to the configured read timeout,
     * bounds the wait until the response headers arrive; whether it also bounds the body read depends on
     * the JDK (observed: not on JDK 21 and 25, yes on JDK 27), and the body read is bounded in size by the
     * ceiling this instance was constructed with, see {@link #exchange}.
     * </p>
     *
     * @param client         the client from {@link #sharedClient(HttpHandler)}; must not be {@code null}
     * @param request        the request to send; must not be {@code null}
     * @param failureContext a short, caller-safe prefix identifying the endpoint for exception messages
     * @return the response, its body read under the ceiling this instance was constructed with
     * @throws IOException              if the exchange fails, including a body over the ceiling, a request
     *                                  timeout, a cancelled exchange and a response-side checked or runtime
     *                                  failure mapped to the fixed text {@code "the HTTP exchange failed"}
     * @throws InterruptedException     if the calling thread is interrupted before the request is sent,
     *                                  in which case nothing is sent, or while waiting, see {@link #exchange}
     * @throws TransportException       if the HTTP client refuses the response as malformed
     * @throws IllegalArgumentException if the HTTP client refuses the request before sending it
     */
    public HttpResponse<String> send(HttpClient client, HttpRequest request, String failureContext)
            throws IOException, InterruptedException {
        Objects.requireNonNull(client, "client must not be null");
        Objects.requireNonNull(request, "request must not be null");
        return exchange(client, request, bodyHandler(),
                () -> new TransportException(failureContext + ": " + MALFORMED_RESPONSE));
    }

    /**
     * Sends {@code request} through {@code client} and waits for the response, telling a request-side
     * fault from a response-side one by the documented {@link HttpClient#sendAsync} contract: a request
     * the client refuses is thrown synchronously from {@code sendAsync}, while every failure of the
     * exchange, the response included, completes the returned future exceptionally.
     * <p>
     * Every failure of the future is decided on its cause, unwrapped from the {@link ExecutionException} and
     * from any nested {@link CompletionException} or {@link ExecutionException} below it; the unwrap stops
     * after {@code MAX_CAUSE_DEPTH} levels, so a cyclic or over-deep wrapper chain cannot loop and ends as the
     * fixed {@code "the HTTP exchange failed"} failure below. A failure that
     * {@link #isMalformedFraming(Throwable)} recognises on that cause is a response the client refused as
     * malformed: a {@code Content-Length} that is not a number, which the JDK HTTP/1.1
     * client parses itself, for a {@code 204} or other no-body reply even before any body handler runs. It is
     * replaced by the exception {@code malformedResponse} supplies; the JDK's exception is neither chained
     * nor reused, because its message carries the raw header value (CWE-117, CWE-532). An
     * {@link IllegalArgumentException} thrown synchronously is a request-side fault and reaches the caller
     * as thrown. Every other failure of the future reaches the caller as an {@link IOException} or an
     * {@link Error}, so a caller's {@link IOException} handling covers each response-side failure:
     * </p>
     * <ul>
     *   <li>an {@link IOException} cause is rethrown as is;</li>
     *   <li>a {@link CancellationException} cause is replaced by an {@link IOException} with the fixed text
     *       {@code "the HTTP exchange was cancelled"} and no chained cause, the same text a future cancelled
     *       while waiting raises;</li>
     *   <li>an {@link Error} cause is rethrown as is;</li>
     *   <li>every other cause, checked or runtime — an {@link IllegalArgumentException} that is not the
     *       malformed-framing form included, and a wrapper left over when the unwrap bound is reached — is
     *       replaced by an {@link IOException} with the fixed text {@code "the HTTP exchange failed"} and no
     *       chained cause, because a response-side failure message can carry remote content; only the
     *       cause's class name is logged at debug level.</li>
     * </ul>
     * <p>
     * The wrapped-cancellation, nested-wrapper, checked-cause and runtime-cause mappings are defensive: no JDK
     * path that completes the future with any of these shapes is known from reading the client, so they
     * guard the caller against a raw exception or remote text rather than map an observed failure.
     * </p>
     * <p>
     * The wait has no bound of its own. The timeout of {@code request} bounds it until the response headers
     * arrive: every back-channel request is built through {@link HttpHandler#requestBuilder()}, which sets
     * that timeout to the configured read timeout, and the client completes the future with an
     * {@link java.net.http.HttpTimeoutException} when no response headers arrive within it. Whether the
     * request timeout also bounds the body read depends on the JDK: on JDK 21 and 25 it was observed not
     * to, so a server that sends the headers and then stalls the body keeps the calling thread waiting; on
     * JDK 27 it was observed to, so such a call ends with an {@link IOException}: the
     * {@link java.net.http.HttpTimeoutException} of the request timeout, or the plain {@link IOException} the JDK
     * reports for the body it cut short, and which of the two arrives varies. On every JDK the body
     * read is bounded in size by {@code bodyHandler} — the {@link BoundedBodyHandler} ceiling on the
     * back-channel path — and that size bound, not a time bound, is what this method relies on.
     * </p>
     * <p>
     * A calling thread that is already interrupted when this method is entered sends nothing: the
     * interrupt status is cleared and an {@link InterruptedException} is thrown before
     * {@link HttpClient#sendAsync} is called, as {@link HttpClient#send} does. The request therefore never
     * reaches the server, so a grant presented to the token endpoint is not redeemed. An interrupt that
     * arrives while waiting for the response throws the same exception after the request may have been
     * sent, and cancels the exchange. Either way the caller handles the interrupt on one path.
     * </p>
     *
     * @param client            the client to send through; must not be {@code null}
     * @param request           the request to send; must not be {@code null}
     * @param bodyHandler       the body handler for the response; must not be {@code null}
     * @param malformedResponse supplies the caller-safe exception that replaces a response-side
     *                          malformed-framing failure; must not be {@code null}
     * @param <T>               the response body type
     * @return the response
     * @throws IOException              if the exchange fails, times out or is cancelled, including a
     *                                  response-side checked or runtime failure mapped to the fixed text
     *                                  {@code "the HTTP exchange failed"} and a wrapped cancellation mapped
     *                                  to {@code "the HTTP exchange was cancelled"}
     * @throws InterruptedException     if the calling thread is already interrupted on entry, in which case
     *                                  nothing is sent, or is interrupted while waiting, in which case the
     *                                  exchange is cancelled
     * @throws IllegalArgumentException if the client refuses the request before sending it
     */
    public static <T> HttpResponse<T> exchange(HttpClient client, HttpRequest request,
            HttpResponse.BodyHandler<T> bodyHandler, Supplier<? extends RuntimeException> malformedResponse)
            throws IOException, InterruptedException {
        Objects.requireNonNull(malformedResponse, "malformedResponse must not be null");
        // sendAsync has no interrupt check of its own: an interrupted caller must not dispatch the request.
        if (Thread.interrupted()) {
            throw new InterruptedException("the calling thread was interrupted before the request was sent");
        }
        // A request the client refuses is thrown here, synchronously, and reaches the caller as thrown.
        CompletableFuture<HttpResponse<T>> pending = client.sendAsync(request, bodyHandler);
        try {
            // The request timeout (the read timeout, set by HttpHandler.requestBuilder()) bounds this wait
            // until the response headers arrive; whether it bounds the body read after them depends on the
            // JDK (observed: not on 21 and 25, yes on 27), the body's size bound applies on every JDK.
            return pending.get();
        } catch (InterruptedException e) {
            pending.cancel(true);
            throw e;
        } catch (CancellationException e) {
            throw new IOException(EXCHANGE_CANCELLED, e);
        } catch (ExecutionException e) {
            throw exchangeFailure(e, malformedResponse);
        }
    }

    /**
     * Maps the failure of an exchange's future to what {@link #exchange} reaches its caller with, as that
     * method documents: the cause is unwrapped from {@code failure} and from any nested
     * {@link CompletionException} or {@link ExecutionException}, for at most {@code MAX_CAUSE_DEPTH} levels;
     * a malformed-framing cause is replaced by the exception {@code malformedResponse} supplies, an
     * {@link Error} cause is rethrown as is, and every other cause is mapped to the returned
     * {@link IOException}.
     *
     * @param failure           the failure of the exchange's future
     * @param malformedResponse supplies the caller-safe exception that replaces a malformed-framing failure
     * @return the {@link IOException} the caller is to throw: the {@link IOException} cause itself, the
     *         fixed text {@code "the HTTP exchange was cancelled"} for a wrapped cancellation, or the fixed
     *         text {@code "the HTTP exchange failed"} without a chained cause for every other cause, checked
     *         or runtime, and for a wrapper chain deeper than the unwrap bound
     * @throws RuntimeException the exception {@code malformedResponse} supplies, for a malformed-framing cause
     * @throws Error            an {@link Error} cause, rethrown as is
     */
    private static IOException exchangeFailure(ExecutionException failure,
            Supplier<? extends RuntimeException> malformedResponse) {
        Throwable cause = failure.getCause();
        for (int depth = 0; depth < MAX_CAUSE_DEPTH && isWrapper(cause) && cause.getCause() != null; depth++) {
            cause = cause.getCause();
        }
        if (isMalformedFraming(cause)) {
            // Deliberately neither chained nor reused: the JDK's message carries the raw header value.
            LOGGER.debug("The HTTP client refused the response as malformed");
            throw malformedResponse.get();
        }
        if (cause instanceof IOException ioException) {
            return ioException;
        }
        if (cause instanceof CancellationException) {
            // Defensive: no JDK path is known to fail the future with a wrapped cancellation.
            return new IOException(EXCHANGE_CANCELLED);
        }
        if (cause instanceof Error error) {
            throw error;
        }
        // Defensive: deliberately not chained, a response-side failure message can carry remote content.
        LOGGER.debug("The HTTP exchange failed with a failure of type %s",
                cause == null ? "none" : cause.getClass().getName());
        return new IOException(EXCHANGE_FAILED);
    }

    private static boolean isWrapper(@Nullable Throwable failure) {
        return failure instanceof CompletionException || failure instanceof ExecutionException;
    }

    /**
     * @return a bounded body handler that enforces this instance's {@code maxContentSize} ceiling during
     *         the read (M7)
     */
    public HttpResponse.BodyHandler<String> bodyHandler() {
        return BoundedBodyHandler.ofString(StandardCharsets.UTF_8, maxContentSize, boundOrigin);
    }
}
