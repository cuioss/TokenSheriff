# Code Review — `token-sheriff-validation` (commons base + validation trust core)

**Scope reviewed:** the entire `token-sheriff-validation` Maven module — both the **commons**
base layer (`de.cuioss.sheriff.token.commons.*`: transport, error, events, metrics) and the
**validation** trust core (`…validation.*`: pipeline, domain/claims, jwks/keys, jwe, dpop, cache,
json parsing, security, util). ~17,400 LOC production, ~34,500 LOC test, ~118 main files.

**Reviewed against:** the module's own requirements and specs — `doc/validation/**`
(VALIDATION-1..12, ~51 IDs; architecture; threat-model; security-reference; the MP-JWT,
multi-IdP and token-decryption specs) and `doc/commons/**` (COMMONS-1..15; transport, error-model
and observability specs) — and the RFCs those make normative (RFC 7515/7516/7517/7518/7519/7638/
8725/9068/9449, NIST SP 800-131A, OIDC Core).

**Method:** six independent deep reviews (commons transport/error/events/metrics; the crypto core
JWKS/keys/algorithm-policy/JWE; the pipeline claims/domain/multi-issuer; parsing-hardening/DPoP/
cache/util; a full VALIDATION+COMMONS conformance sweep; and test completeness), each reading the
real code, followed by first-hand verification of every critical/high finding against source.

**Relationship to the client review:** this is the module the `token-sheriff-client` engine
(reviewed in [`doc/code-review.md`](code-review.md)) depends on and inherits its token-trust and
transport posture from. One finding here (C1, SSRF) is the other half of that review's H9 and is
**cross-cutting** — see the callout under C1.

---

## Executive summary

**This is the inverse of the client-module result.** Where the client review found strong
components left unwired, here the **trust core is genuinely well-built _and_ wired**: the
conformance sweep found **no orphaned/dead-code defenses** — DPoP, JWE decryption, key-rotation
grace period, custom validation rules, and the metrics monitor are all invoked from a real
`TokenValidator` path. The cryptographic heart is **fail-closed with no discovered fail-open
path**: `alg=none` and HMAC are rejected in depth (no RS256↔HS256 key-confusion route exists), the
signature is verified before any claim is trusted, `alg` is bound to the key type, multi-issuer
routing is correctly key-bound (choosing `iss` only selects the *correct* verifying key set),
mandatory `exp`/`iat` claims are enforced with correct clock-skew signs (`sub` is likewise
mandatory unless `claimSubOptional` is enabled — see DOC-3), the token cache never
returns a result that outlives `exp`, and JWE does verify-then-decrypt with a constant-time MAC, a
256 KB decompression cap, and CEK zeroing. Test hygiene is clean (no `Thread.sleep`, no unseeded
randomness, no real network — with one exception: a real 2s wall-clock wait in the cache-expiry
test despite an injectable clock, see L13) and much of the public entry point *is* exercised
adversarially.

**The real defects cluster in two places:**

1. **The commons transport layer under-delivers its own hardening requirements.** SSRF defense
   (COMMONS-3) is **entirely absent**; cleartext HTTP is **allowed by default** (COMMONS-2 MUST);
   response bodies are **buffered unbounded** before the size check, and the JWKS path has no size
   bound at all (COMMONS-4). These are the highest-severity items and they degrade the whole
   system's transport posture — including the client engine that delegates SSRF here.
2. **Test honesty regresses at seven load-bearing security points** (the psychic-signature
   defense, H3, plus the six further defenses tabulated in H4) — the same *isolated-green-while-
   the-wired-path-never-reaches-the-attack* blind spot the client review named, in a narrower but
   still serious form. The flagship one: the psychic-signature (CVE-2022-21449) test passes for an
   unrelated reason, so that defense is effectively untested — while the threat-model coverage
   table reports Spoofing/Tampering/EoP/DPoP at 100%.

Plus a scattering of correctness/hardening items (weak-RSA acceptance, a DPoP replay window, a
DPoP header-casing bypass, custom-rule bypass on cache hit) and several **documentation-accuracy**
defects where the docs and code disagree in *both* directions.

### Severity tally

- **Critical: 1** — SSRF defense entirely absent (COMMONS-3), and it is the delegation target the client module relies on.
- **High: 4** — cleartext HTTP by default; unbounded response buffering (+ JWKS unbounded); psychic-signature defense untested; systemic isolated-vs-wired test blind spot across six further defenses (seven total including H3).
- **Medium: ~8** — weak-RSA keys accepted; discovery cached forever + permanent negative cache; DPoP replay window; DPoP header-casing bearer bypass; custom rules skipped on cache hit; dishonest/missing tests (HMAC-confusion, decompression-bomb, concurrency); issuer not a pre-fetch gate; inconsistent transport timeouts.
- **Low/info: many** — DPoP parser-bound divergence, JSON depth unbounded (+ false javadoc), replay-cache flood eviction, thumbprint JSON escaping, `azp→aud` fallback, COMMONS-11 unimplemented, and three doc-vs-code accuracy defects.

**Recommendation:** the trust core is release-grade. The **commons transport hardening (C1/H1/H2)
is not** — for a security-first library those are the gating items, and C1 in particular means
"SSRF is handled by commons" is currently false everywhere it is claimed. The test-honesty items
(H3/H4) should be fixed before the threat-model coverage table is trusted, because they currently
certify defenses that aren't on the executed path.

---

## Critical

### C1 — SSRF defense on IdP-advertised URLs is entirely absent (COMMONS-3 unmet)
*COMMONS-3, T-SSRF / T-DISCOVERY · `commons/transport/HttpJwksLoaderConfig.java:209-226`, `WellKnownConfig.java:237-253`, consumed at `jwks/http/HttpJwksLoader.java:204`*

COMMONS-3 requires that any URL from an IdP-controlled source (`jwks_uri`, discovery endpoints,
and every advertised endpoint) be constrained before fetch: HTTPS-only, host allow-list/egress
policy, and internal/link-local/loopback ranges blocked. **None of this exists.** A repo-wide
sweep confirms no `InetAddress`/`isLoopback`/`isSiteLocal`/`isLinkLocal`/`169.254`/allow-list guard
anywhere in the module — "SSRF" appears only in javadoc, and `TransportException`'s javadoc
advertises an "SSRF-blocked" branch that no code path ever raises. DNS-rebinding (re-checking the
resolved address post-DNS) is not considered at all. An attacker who tampers with or spoofs
discovery metadata can advertise `jwks_uri: http://169.254.169.254/latest/meta-data/…` (or any
internal address) and the server will fetch it — exactly the motivating CVE-2026-1180 that the
requirements exclusion table cites.

> **Cross-cutting with the client review (H9).** [`doc/code-review.md`](code-review.md) found the
> client engine issues its own HTTP and *delegates SSRF to commons (COMMONS-3)*. This finding shows
> the delegation target is empty. Net effect across both modules: **no layer in the system
> implements SSRF defense on IdP-advertised URLs** — not the client, not commons. The two reviews
> together turn "each layer assumes the other does it" into a confirmed system-wide gap.

**Fix:** add an egress guard in commons applied to every advertised URL before fetch — reject
resolved loopback/link-local/site-local/ULA/metadata addresses, enforce an optional host
allow-list, and re-check the address after DNS resolution. Then the client's delegation becomes
true rather than aspirational.

---

## High

### H1 — Cleartext HTTP permitted by default (COMMONS-2 MUST violated)
*COMMONS-2 / VALIDATION-8.3, T-TLS · `commons/transport/HttpJwksLoaderConfig.java:285`, `WellKnownConfig.java:110`*

COMMONS-2 states "Plain HTTP endpoints MUST be rejected." Both commons config builders hardcode
`allowInsecureHttp = true` as the default — and the javadoc documents it as "`true` (default) to
allow cleartext HTTP." Commons thereby **overrides cui-http's own secure-by-default** (`false`),
so plaintext discovery/JWKS is accepted with only a WARN; rejection is opt-in. Any operator who
forgets `allowInsecureHttp(false)` is exposed to a MITM/downgrade on JWKS resolution → key
substitution (T-TLS). The TLS-floor half of COMMONS-2 (TLS 1.2+, hostname verification) *is*
satisfied by cui-http; only the reject-plaintext half is inverted. **Fix:** default
`allowInsecureHttp` to `false` in both builders; require explicit opt-in for cleartext.

### H2 — Response-size limit applied only after full buffering; JWKS path has no size bound
*COMMONS-4, T-DOS / VALIDATION-8.1 · `commons/transport/WellKnownConfigurationConverter.java:80-87,132`, `JwksHttpContentConverter.java:59-82`*

The discovery converter checks `maxContentSize` *after* `BodyHandlers.ofString()` has already
buffered the entire body into memory; the JWKS converter has **no overall size check at all**
(DSL-JSON's `limitStringBuffer=4KB` bounds only individual strings, not document size or key
count). A hostile or degraded IdP returning a multi-gigabyte (or slow-drip within the read
timeout) body exhausts heap before any check fires — the size gate as written defends nothing on
the discovery path and is absent on the JWKS path. The threat model delegates response-DoS here
and claims "no unbounded buffering." **Fix:** enforce a byte ceiling during streaming (bounded
`BodySubscriber` / `Content-Length` pre-check + capped read) before materializing the body, and
apply the same ceiling to JWKS. (The DPoP proof path has an analogous but hardcoded 8 KB cap — see
HARD-2.)

### H3 — Psychic-signature (CVE-2022-21449) defense is untested; the test passes for an unrelated reason
*VALIDATION-12.1, threat S1/T4 · `security/PsychicSignatureAttackTest.java:57,65-92`; `pipeline/validator/TokenSignatureValidator.java:139-151`*

`PsychicSignatureAttackTest` mints ES256/384/512 tokens with all-zero signatures, but builds the
issuer with `InMemoryJWKSFactory.createDefaultJwks()` — an **RSA** key under the default `kid`
that the token keeps. `TokenSignatureValidator.validateSignature` rejects at
`isAlgorithmCompatible("ES256","RS256")` (line 139, `UNSUPPORTED_ALGORITHM`) **before**
`verifySignature` (line 151), and the test asserts exactly `UNSUPPORTED_ALGORITHM`. The all-zero
ECDSA signature never reaches the verifier — **if the JDK were vulnerable, this test would still
pass.** Production has no explicit `r ≠ 0 / s ≠ 0 / r,s < n` check (verified: no such guard in
`TokenSignatureValidator`), so the CVE-2022-21449 defense relies entirely on the JDK version and
nothing exercises it. **Fix:** configure a matching EC JWKS under the same `kid` so the zero
signature reaches the verifier and assert `SIGNATURE_VALIDATION_FAILED`; consider an explicit
component-range check so the defense is in code, not just the platform.

### H4 — Systemic: six further security defenses are green in isolation while the wired path never reaches the attack
*VALIDATION-12.1; threat-model coverage table (claims Spoofing/Tampering/EoP/DPoP = 100%)*

The isolated-vs-wired blind spot the client review named recurs here — narrower (the defenses are
*reachable*, unlike the client's unwired ones) but still certifying protection that the executed
path doesn't demonstrate. Beyond H3, the pattern holds at these load-bearing points:

| Defense | What the "attack" test actually does | Where the real defense is (only) proven |
|---|---|---|
| **HMAC/RSA algorithm confusion** (VTEST-2) | `shouldRejectAlgorithmConfusionAttack` swaps `kid` and asserts `KEY_NOT_FOUND`; the tampering util rewrites `alg=HS256` but keeps the RS256 signature bytes — no HMAC is ever computed over the RSA public key | header allow-list, isolated (`TokenHeaderValidatorTest`) |
| **JWE decompression bomb** (VTEST-3) | no test feeds a `zip:"DEF"` payload that inflates past the 256 KB bound | production bound exists (`JweDecryptor.java:337`) but is never exercised |
| **Cross-issuer key substitution** (VTEST-6) | wired coverage only for `NO_ISSUER_CONFIG` / missing-kid | "same kid, different key material → `SIGNATURE_VALIDATION_FAILED`" only on `TokenSignatureValidator` directly |
| **JWE alg allow-list** (VTEST-7) | RSA1_5/unsupported-`enc` rejection only against `JweDecryptor`; wired `TokenValidator` JWE tests assert only wrong-key/no-config | isolated only |
| **DPoP sender-binding** (VTEST-8) | wired test covers replay (DP1) genuinely, but DP2 `cnf.jkt` mismatch, DP3 stale-iat, DP4 HS256 are isolated-only | `DpopProofValidatorTest` only |
| **jku/x5u & embedded-JWK** (VTEST-9) | wired tests tamper the header (breaking the signature), so `assertThrows` is satisfied by incidental `SIGNATURE_VALIDATION_FAILED`, not the named defense; counter assertions are `>= 0` (always true) | embedded-JWK honest in isolation (`TokenHeaderValidatorTest`) |

Same required fix as the client module: a **wired negative-path tier** that drives the real
`TokenValidator` entry point with the specific crafted attack and asserts the *specific* rejection
event — and a rule that the threat-model coverage table may not mark a threat 100% without one.
The table currently overstates coverage for exactly these rows.

---

## Medium

- **M1 — RSA keys shorter than 2048 bits are accepted** (*VALIDATION-1.3 MUST-NOT, NIST SP 800-131A · `jwks/key/JwkKeyHandler.java:76`, `jwks/parser/KeyProcessor.java:146`, DPoP `DpopProofValidator.java:404`*). No modulus bit-length check exists anywhere (verified: no `bitLength`/`2048` guard in the module). A JWKS endpoint serving a 512/1024-bit RSA key is accepted and RS256/PS256 tokens signed with a factorable modulus validate. The requirement is explicit ("RSA … keys shorter than 2048 bits … shall NOT be supported"). Fix: reject `modulus.bitLength() < 2048` in `parseRsaKey` and on the DPoP embedded-JWK path.
- **M2 — Discovery cached forever; a transient first-load failure pins the resolver permanently** (*COMMONS-8 · `commons/transport/HttpWellKnownResolver.java:91-103`*). `cachedResult.updateAndGet(cur -> cur == null ? load : cur)` stores the *first* `HttpResult` — including a failed one — permanently; after a transient network failure at first load every later call returns the cached failure with no re-fetch, and on success there is no TTL/ETag revalidation, so discovery never refreshes (contradicting COMMONS-8 "bounded lifetime"). Fix: don't cache failures (short negative-TTL + recovery); add a bounded success TTL.
- **M3 — DPoP replay window from TTL/freshness decoupling** (*VALIDATION-8.7, RFC 9449 §11.1 · `dpop/DpopConfig.java:159-176`, `DpopReplayProtection.java`, `DpopProofValidator.java:313-321`*). `build()` validates `proofMaxAgeSeconds` and `nonceCacheTtlSeconds` independently but never enforces `ttl ≥ proofMaxAge + clockSkew`. With defaults (both 300s) and the freshness check accepting future-dated proofs up to `clockSkew` ahead, a proof's jti entry evicts at `firstSeen+300` while it stays "fresh" until `firstSeen+360` — the same jti can be **replayed in that ~60s window**, and arbitrarily wider if an operator lowers the TTL below the proof max-age. Fix: enforce the invariant in `build()`, or derive the replay TTL from the freshness window.
- **M4 — DPoP header lookup is case-sensitive `"dpop"`; RFC-canonical `"DPoP"` is silently ignored** (*VALIDATION-8.7 · `dpop/DpopProofValidator.java:72,171`, `domain/context/AccessTokenRequest.java`*). `DPOP_HEADER_NAME = "dpop"` with a case-sensitive `httpHeaders().get(...)`, and `AccessTokenRequest.httpHeaders` carries no key-casing contract. An integrator supplying the RFC-canonical `"DPoP"` key gets the proof silently ignored; with `dpop.required=false` (default) the sender-constrained token then passes in **bearer mode** — a latent downgrade bypass saved only by the edge happening to lowercase headers (untested at this boundary). Fix: look up case-insensitively, or specify and enforce a normalized header-map contract on `AccessTokenRequest`.
- **M5 — Custom `TokenValidationRule`s are bypassed on access-token cache hits** (*VALIDATION-6.1 · `pipeline/AccessTokenValidationPipeline.java:165-171` vs `261-264`*). On a cache hit only DPoP re-runs, then the cached content is returned; configured custom rules run only on the cache-miss path before cache store. A rule that depends on external mutable state (a revocation/blocklist, a time window, a feature flag) is bypassed for the whole cache TTL after the token is first cached. Pure content-deterministic rules are unaffected. Fix: re-run custom rules on the cache-hit branch (as DPoP already is), or document a content-deterministic contract + per-rule "run-on-cache-hit" flag.
- **M6 — Dishonest/blind tests beyond H4** (*VALIDATION-10.1 / 12 · see refs*). `IssuerConfigCacheConcurrencyTest.java:92-94` catches and ignores the exact `UnsupportedOperationException` (the mutable→immutable-map race) it exists to detect, asserting only `totalOperations > 0` — a regressed race on 49/50 threads still passes. JWE decompression-bomb and real HMAC-confusion (H4) are missing outright. The 4 KB string-length limit is only tested just-*under* the bound, never over (`NonValidatingJwtParserTest.java:358-375`; `DSLJsonSecurityTest` sets `maxStringLength(1000)` then never feeds a >1000 string). Fix: assert the race exception is *absent*; add over-limit and bomb tests.
- **M7 — Advertised issuer is not a gate before advertised URLs are fetched** (*COMMONS-5 · `jwks/http/HttpJwksLoader.java:204,405-423`*). The issuer-mismatch path only logs a WARN + counter and returns the configured issuer — *after* the attacker-advertised `jwks_uri` was already fetched at line 204. COMMONS-5 requires validating the advertised `issuer` before advertised URLs are trusted. Defense-in-depth exists (the JWT `iss` is later matched against the configured issuer at validation time, so rogue-IdP tokens still fail), but the SSRF-relevant fetch (C1) is not gated. Fix: constrain advertised URLs independently (C1) and make issuer-mismatch a hard discovery failure.
- **M8 — Inconsistent transport timeout defaults** (*COMMONS-4 · `WellKnownConfig.java:53-58` vs `HttpJwksLoaderConfig`*). Discovery sets explicit low timeouts (connect 2s/read 3s); the direct-JWKS path sets none and silently inherits cui-http's 10s/10s, giving JWKS fetches materially laxer DoS bounds with no stated rationale. Fix: set explicit, documented, consistent timeouts on the JWKS handler.

---

## Low / informational

- **L1 (HARD-2)** — DPoP proof parsing bounds the whole proof at a hardcoded `MAX_DPOP_PROOF_SIZE = 8192` and does **not** apply `ParserConfig.getMaxPayloadSize()`/`getMaxTokenSize()`, unlike the main path (`NonValidatingJwtParser.decodeBase64UrlPart`). An operator who tightens the parser bounds doesn't get them on DPoP proofs (per-string `limitStringBuffer` still applies). (`dpop/DpopProofValidator.java:75,212-230`)
- **L2 (HARD-3 / VTEST-5)** — No JSON nesting-depth or array-size limit exists; DSL-JSON builds the DOM by recursive descent, so a deeply-nested payload within the 8 KB cap can drive a `StackOverflowError` — an uncaught `Error`, not a graceful `TokenValidationException`. And the parser javadoc **advertises `.maxDepth(5)`/`.maxArraySize(10)` builder methods that `ParserConfig` does not expose** (the example would not compile) — a documentation defect masking the missing bound. Fix: add a real depth bound *or* delete the false javadoc and state the size cap is the actual bound. (`NonValidatingJwtParser.java:48-49,94-96`, `ParserConfig.java:161-169`)
- **L3 (HARD-4)** — Replay-cache flooding: an attacker with any valid DPoP keypair can emit unlimited unique-`jti` proofs; once `seenJti` exceeds `maxSize` (10,000) `evictOldest` can drop legitimate `jti`s still inside their freshness window, making those proofs replayable. Memory stays bounded (good), but replay protection degrades under flood. (`dpop/DpopReplayProtection.java:99-133`)
- **L4 (HARD-5)** — `JwkThumbprintUtil.buildJson` concatenates member values into canonical JSON without RFC 7638 string escaping; not exploitable for conformant base64url members (and the `cnf.jkt` binding still requires the matching private key), but not spec-correct canonicalization. (`util/JwkThumbprintUtil.java:89-99`)
- **L5 (HARD-6 / VSPEC-4)** — `htu` normalization strips query/fragment but not scheme/host case, default ports, or path dot-segments; because both sides pass through the same function this only causes **false rejects** (fail-closed), never acceptance of a wrong-endpoint proof. Related: when a proof is present but request URI/method are absent, validation **throws** (fail-closed) — correct security-wise, but an integration footgun if the edge doesn't populate them. (`dpop/DpopProofValidator.java:348-402`)
- **L6 (CRYP-2)** — ECDH-ES ephemeral key: the code checks the EPK shares the private key's curve params but never independently asserts the point lies on the curve, delegating point-validation to the JCA provider. On current SunEC (JDK 17+) this is mitigated; residual risk is provider-dependent (invalid-curve attack). (`jwe/JweDecryptor.java:196-241`)
- **L7 (CRYP-3)** — RSA-OAEP with SHA-1 (MGF1-SHA-1) is enabled by default (Keycloak default; not Bleichenbacher-exploitable, no practical SHA-1-in-OAEP break) — informational; prefer RSA-OAEP-256 where interop allows. (`jwe/JweAlgorithmPreferences.java:74-76`)
- **L8 (CRYP-4)** — Duplicate `kid` in a JWKS is last-one-wins with no warning; impact limited (the shadowing key still comes from the same trusted document and the signature must still verify). (`jwks/key/JWKSKeyLoader.java:384-396`)
- **L9 (CRYP-5)** — A successful JWKS fetch that parses to zero usable keys still swaps the new (empty, ERROR-status) loader into `currentKeys` and retires the good one — `updateKeys` never checks the new loader's status before swapping. Availability degradation (valid tokens fail until the grace period covers), not a bypass. (`jwks/http/HttpJwksLoader.java:266-303`)
- **L10 (PIPE-2/3)** — Ungated `azp → expectedAudience` fallback: an access token missing `aud` is accepted if its `azp` is in `expectedAudience` (conflates client-id with audience; no disable flag), and `client_id` substitutes for a missing `azp` per RFC 9068 with no opt-out. Both realistically constrained (IdP-set values), low confused-deputy risk. (`pipeline/validator/AudienceValidator.java:86-120`, `AuthorizedPartyValidator.java:88-102`)
- **L11 (PIPE-4/5/6)** — No "iat not unreasonably in the future" guard (only `exp`/`nbf` bound validity); mandatory-claim check is presence-only (`containsKey`), so an empty-string `sub`/`iss` counts as present (low impact — bounded by other validators); no duplicate-JSON-key rejection (RFC 8725 §2.4 hardening; not exploitable since the signature covers exact bytes).
- **L12 (COMMONS-11)** — Inbound IdP error normalization (`error`/`error_description` → typed model) is unimplemented; `TransportException` javadoc lists "SSRF blocked"/"TLS rejected" branches no code constructs. Forward-looking (the client-facing endpoints are contracts-only today), but currently unmet and the javadoc over-claims. (`commons/error/`, `commons/transport/`)
- **L13 (VTEST-11/12)** — Concurrency guarantees asserted weakly: JWKS refresh single-flight is never asserted as request-count==1 under concurrent load; the `maxRetiredKeySets` bound is config-checked but not behaviorally enforced; `TokenValidatorConcurrencyTest` asserts "passes if no exception." Cache-expiry test uses a real 2s wall-clock wait despite an injectable clock. Determinism is otherwise clean.

### Documentation-accuracy defects (both directions)

Unlike the client module (where docs *overstated* the code), here the docs and code disagree in
both directions — each is a real accuracy defect for a security-first library whose threat model
is an audit artifact:

- **DOC-1 — threat model *understates* the code (DPoP htm/htu).** `threat-model.adoc` DP5 states "`htm` and `htu` validation is **not** performed by the library; this is handled at the application/framework level." The code **does** enforce both (`DpopProofValidator.validateHtuHtm`, lines 348-385), strips query/fragment per RFC 9449, and refuses to skip them when a proof is present. An auditor reading the threat model would wrongly conclude a control is absent. Fix: update DP5 to reflect that the library enforces htm/htu.
- **DOC-2 — parser javadoc *overstates* the code (JSON depth).** `NonValidatingJwtParser` javadoc advertises `.maxDepth(5)`/`.maxArraySize(10)` config that does not exist (L2). Fix: remove or implement.
- **DOC-3 — security-reference *understates* the code (iat/sub).** `security-reference.adoc` says `iat` is "not actively validated" and `sub` is "mandatory and validated," but `ExpirationValidator` does validate `iat` via `maxTokenAgeSeconds`, and `sub` can be made optional via `claimSubOptional`. Minor; also VALIDATION-2.1 lists `jti` but there is no typed `getJwtId()` accessor. Fix: align the reference with actual behavior.
- **DOC-4 — threat-model coverage table overstates test coverage.** The 39/41 (95%) "mitigated + test-covered" table marks Spoofing/Tampering/EoP/DPoP at 100%, but H3/H4 show several of those cells are certified by isolated or incidental-pass tests. Fix: don't mark a threat covered without a wired negative-path test (ties to H4).

---

## Verified-good defenses (regression anchors — keep these)

**Crypto core (fail-closed, no fail-open path found):**
- `alg=none` and HMAC (HS256/384/512) rejected in depth — `SignatureAlgorithmPreferences.REJECTED_ALGORITHMS`, the header allow-list before key lookup, and `JwkAlgorithmPreferences` for JWK parsing. No RS256↔HS256 key-confusion route exists (an RSA public key can never reach an HMAC verifier).
- Signature verified **before** any claim is trusted; `alg` bound to the key's JWA identifier (RSA family cross-accepts RS*/PS* per RFC 7517 §4.4; EC/EdDSA require exact match); `SignatureTemplateManager` is a second allow-list gate.
- EC signature encoding handled correctly (IEEE P1363 raw R‖S → ASN.1/DER, correct component sizes), shared identically by the JWT and DPoP paths.
- Embedded-JWK header rejected (CVE-2018-0114); `kid` mandatory; unknown `kid` does **not** trigger a JWKS fetch (no DoS amplification / negative-cache needed).
- **JWE**: key-mgmt/content allow-lists enforced before crypto; RSA1_5 rejected even if re-added; AEAD verified before use; AES-CBC-HS is verify-then-decrypt with constant-time `MessageDigest.isEqual` (no padding oracle); Concat-KDF per RFC 7518 §4.6.2; decompression bounded to 256 KB with raw-DEFLATE; CEK/keys zeroized in `finally`; nested JWE forbidden and the inner JWS fully re-validated through the same pipeline.
- **No unwired defenses** — DPoP, JWE, grace-period rotation, custom rules and the metrics monitor are all invoked from a real `TokenValidator`/`AccessTokenValidationPipeline` path (the conformance sweep confirmed this explicitly; contrast the client module).

**Pipeline / claims:**
- Multi-issuer routing is key-bound: `iss` selects that issuer's own JwksLoader, so a token cannot be validated against another issuer's keys — choosing `iss` only picks the *correct* verifying key set.
- `exp` mandatory (missing → reject); clock-skew signs correct (skew extends validity in the right direction only); strict numeric typing on `exp`/`iat`/`nbf` (string/overflow/malformed throw); `iss` exact-match (no prefix/substring); audience enforced when configured and mandatory for ID tokens, with `IssuerConfig.build()` forcing a non-empty `expectedAudience` unless audience validation is explicitly disabled (closes the accidental no-audience fail-open).
- Access-token and ID-token paths share one `TokenClaimValidator` — no rule drift.

**Cache / DPoP / parsing:**
- Token cache keyed by SHA-256 of the raw token; `CachedToken.verifyToken` re-compares the raw token on every hit; `isExpired` (with skew) re-checked on every hit — **a cached positive never outlives `exp`**; no negative caching (no poisoning); bounded size + race-safe `putIfAbsent`.
- DPoP re-validated with the *current* request's proof on every cache hit (no bypass); replay `jti` stored only after signature verification; same algorithm allow-list and crypto path as the main pipeline; `ath`, `iat` two-sided freshness, and RFC 7638 thumbprint ordering correct; htu/htm now fail-closed.
- Size limits: token/payload 8 KB pre-pipeline; `MAX_KEYS_COUNT=50`; `kid` length ≤100; JWE token size enforced via `TokenStringValidator` (not dead config).

**Commons non-transport & boundary:**
- `SecurityEventCounter` thread-safe (`ConcurrentHashMap`+`AtomicLong`), keyed by a bounded enum (no cardinality explosion), records no token material/secrets/URLs; `EventCategory` and `MetricIdentifier` carry no Micrometer/validation dependency (COMMONS-13/14/15).
- Typed exception hierarchy; library throws typed exceptions and never renders problem+json; exception messages safe (URLs only in WARN/DEBUG logs) — COMMONS-9/12 hold within commons.
- TLS 1.2+ floor pinned on the wire; **redirects not followed** (JDK `Redirect.NEVER`), neutralizing internal-range/downgrade-redirect vectors; retry bounded, jittered, `idempotentOnly=true` (no storms). The **ArchUnit commons→validation boundary is real and enforced** (`architecture/CommonsLayeringTest`).

**Test suite (the strong majority):** signature-tamper, `alg=none`, `iss`/`aud`/`azp`, `kid`
injection (properly re-signed), size limits, expiry/`nbf`/skew (deterministic injectable clock),
EC R‖S↔DER, embedded-JWK, JWKS rotation grace period, and DPoP replay are all driven end-to-end
through the real `TokenValidator` with the correct event asserted. No sleeps, no unseeded
randomness, no real network.

---

## Suggested remediation order

1. **Implement SSRF defense in commons (C1)** — it is the delegation target the whole system (including the client engine) assumes exists. Egress guard on every advertised URL, with post-DNS re-check. This closes both this review's C1 and the client review's H9.
2. **Flip cleartext to opt-out and make the size limit real (H1, H2)** — default `allowInsecureHttp=false`; enforce a streaming byte ceiling on discovery *and* JWKS before buffering.
3. **Fix the test honesty at the load-bearing points (H3, H4)** and add the wired negative-path tier — reach the real attack through `TokenValidator` and assert the specific event; then reconcile the threat-model coverage table (DOC-4) so it stops claiming 100% for incidentally-passing cells.
4. **Reject weak RSA keys (M1)** — `< 2048` bits, on both the JWKS and DPoP-embedded-JWK paths.
5. **Close the DPoP gaps (M3, M4)** — enforce `ttl ≥ proofMaxAge + skew`; make the DPoP header lookup case-insensitive (or contract the header map).
6. **Fix discovery caching (M2)** and the cache-hit custom-rule bypass (M5); align transport timeouts (M8) and gate on advertised issuer (M7).
7. **Reconcile the documentation (DOC-1..3)** — the threat model and security-reference must match the code in both directions; a stale security document is itself a defect for an audited library.
8. Sweep the low/info items (parser depth bound + false javadoc, DPoP parser-bound divergence, thumbprint escaping, ECDH point validation, duplicate-kid, empty-key-set retirement, concurrency assertions).
