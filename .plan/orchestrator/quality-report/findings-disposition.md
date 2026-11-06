# Findings Disposition — re-grounded at HEAD `aa77acd9` (2026-10-28)

Source: `source-report/` (snapshot of `d6b927c`, 2026-08-15). Every finding was re-checked against
the implementing source at `aa77acd9` (four parallel read-only verification passes, each claim
re-read in code; a sample of 14 claims was independently re-checked by the orchestrator — all
corroborated). Line numbers in `source-report/` are stale; the owning plan spec carries current
locations.

Verdict vocabulary: **VALID** (holds unchanged), **PARTIAL** (partly fixed, remainder owned by a
plan), **FIXED** (no work left), **CHANGED** (code restructured; residual noted), **NO-ACTION**
(valid observation, deliberately not fixed — rationale given).

## Validation code (V)

| ID | Sev | Verdict at HEAD | Owner |
|---|---|---|---|
| V-1 | major | **FIXED** — #835 (e010c5cf), PLAN-05; `epk` is a typed `JwkKey`, ECDH-ES works through the production parser (closed per PR; code not re-read) | PLAN-05 |
| V-2 | major | **FIXED** — #833 (2ea3ef2f), PLAN-03; hit path and eviction re-apply the issuer's `maxTokenAgeSeconds` (closed per PR; code not re-read) | PLAN-03 |
| V-3 | major | **FIXED** — #830 (1ca5ab35), PLAN-01 (closed per PR; merge state corroborated, code not re-read) | PLAN-01 |
| V-4 | major | **FIXED** — #830 (1ca5ab35), PLAN-01; transient discovery failure retried, absent advertised issuer now terminal (closed per PR; code not re-read) | PLAN-01 |
| V-5 | major | **FIXED** — #830 (1ca5ab35), PLAN-01; plus Quarkus `TokenValidatorProducer` `@PreDestroy` (closed per PR; code not re-read) | PLAN-01 |
| V-6 | major | **FIXED** — #818 (9fc9d4d3) gate propagation, #817 (6d45da13) error text; `IssuerConfigDiscoveryIssuerTest` | — |
| V-7 | major | **FIXED** — TLS #748, retry #795, connect/read timeouts #844 (50c295e6), PLAN-07; both well-known handlers honour them (closed per PR; code not re-read) | PLAN-07 |
| V-8 | minor | **FIXED** — #830 (1ca5ab35), PLAN-01 (closed per PR; code not re-read) | PLAN-01 |
| V-9 | minor | **FIXED** — #833 (2ea3ef2f), PLAN-03; entries carry their issuer's clock skew (closed per PR; code not re-read) | PLAN-03 |
| V-10 | minor | **FIXED** — #844 (50c295e6), PLAN-07; opt-in max-staleness bound (off by default, `TokenSheriff-170`) and re-discovery after three failed cycles (closed per PR; code not re-read) | PLAN-07 |
| V-11 | minor | **FIXED** — #851 (49ba673d), PLAN-09; the range check lives in `SignatureVerificationUtil.verifySignature` and runs before `initVerify`, so DPoP proofs get it too; wired all-zero ES256 DPoP test (closed per PR; call site and ordering read in merged source) | PLAN-09 |
| V-12 | minor | **FIXED** — #851 (49ba673d), PLAN-09; `checkAndStore(jti)` runs last, after every stateless check (closed per PR; ordering read in merged source) | PLAN-09 |
| V-13 | minor | **FIXED** — #861 (7145aebb), PLAN-13; `JWKS_LOAD_FAILED` and `JWKS_KEYS_UPDATED` templates now match their arguments (closed per PR; the two templates read in the merged diff, other call sites not re-read) | PLAN-13 |
| V-14 | minor | **FIXED** — #861 (7145aebb), PLAN-13; `DUPLICATE_JWKS_KID` is now identifier 171 and `LogMessageIdentifierUniquenessTest` guards uniqueness (read in the merged diff) | PLAN-13 |
| V-15 | minor | **FIXED** — #861 (7145aebb), PLAN-13; `ParserConfig.maxHttpResponseSize` (default 256 KB) is independent of `maxPayloadSize` (read in the merged diff); documentation gap in `architecture.adoc` and `developing.adoc` → PLAN-25 | PLAN-13 |
| V-16 | minor | **FIXED** — #830 (1ca5ab35), PLAN-01 (closed per PR; code not re-read) | PLAN-01 |
| V-17a | info | **FIXED** — #861 (7145aebb), PLAN-13; `EgressPolicy` now blocks `100.64.0.0/10`, `0.0.0.0/8` and `255.255.255.255` (read in the merged diff; the DNS-rebinding wording not re-read) | PLAN-13 |
| V-17b | info | **FIXED** — #851 (49ba673d), PLAN-09; all three production splits use limit `-1` (closed per PR; the three sites read in merged source) | PLAN-09 |
| V-17c | info | **FIXED** — #851 (49ba673d), PLAN-09; `now >= exp + skew` is expired, via one shared predicate (closed per PR; predicate read in merged source) | PLAN-09 |
| V-17d | info | VALID — duplicate JSON keys last-wins (doc only) | PLAN-18 |
| V-17e | info | **FIXED** — #851 (49ba673d), PLAN-09; an EC JWK whose `alg` does not match its curve is dropped, never re-labelled (closed per PR; check read in merged source) | PLAN-09 |
| V-17f | info | **FIXED** — #853 (a9ce05df), PLAN-11; `AudienceValidator` treats only an absent `aud` as missing, an empty array is `AUDIENCE_MISMATCH` and never reaches the `azp` fallback (code read in the merged source) | PLAN-11 |
| V-17g | info | **FIXED** — #835 (e010c5cf), PLAN-05; random CEK on unwrap failure, CBC tag mismatch normalised to the same typed rejection (closed per PR; code not re-read) | PLAN-05 |
| V-17h | info | **FIXED** (PLAN-13 part) — #861 (7145aebb); `JWKS_KEYS_UPDATED` logs issuer and key ids (read in the merged diff); J-1 part still owned by PLAN-17 | PLAN-13 |

## Client code (C)

| ID | Sev | Verdict at HEAD | Owner |
|---|---|---|---|
| C-1 | major | **FIXED** — #832 (31fb2212), PLAN-02 (closed per PR; merge state corroborated, code not re-read) | PLAN-02 |
| C-2 | minor | **FIXED** — #832 (31fb2212), PLAN-02; the PR body files this fix under "C-8b" (closed per PR; code not re-read) | PLAN-02 |
| C-3 | minor | **FIXED** — #856 (02c0fd3a), PLAN-10; checked once at the token-endpoint seam, absent/malformed `cnf.jkt` also rejected (read in merged source) | PLAN-10 |
| C-4 | minor | **FIXED** — #840 (a56b1b59), PLAN-06; shared `EndpointSchemePolicy`, query placed before a fragment (closed per PR; code not re-read) | PLAN-06 |
| C-5 | minor | **FIXED** — #840 (a56b1b59), PLAN-06 (closed per PR; code not re-read) | PLAN-06 |
| C-6 | minor | **FIXED** — #840 (a56b1b59), PLAN-06; `state` verified first (closed per PR; code not re-read) | PLAN-06 |
| C-7 | info | **FIXED** — #840 (a56b1b59), PLAN-06; `FlowContext` is single use, claimed atomically (closed per PR; code not re-read) | PLAN-06 |
| C-8a | info | **FIXED** — #840 (a56b1b59), PLAN-06; one shared `ErrorTextSanitizer` (closed per PR; code not re-read) | PLAN-06 |
| C-8b | info | **FIXED** — #832 (31fb2212), PLAN-02; caps constructor-injectable (closed per PR; code not re-read) | PLAN-02 |
| C-8c | info | **FIXED** — #856 (02c0fd3a), PLAN-10; decided as documentation (PAR is a building block the built-in flows do not use), `requirements.adoc` states it | PLAN-10 |
| C-8d | info | **FIXED** — #856 (02c0fd3a), PLAN-10; `StepUpResultVerifier` takes an `InstantSource` and an opt-in skew (default zero) | PLAN-10 |

## Quarkus / benchmarking / build (Q)

| ID | Sev | Verdict at HEAD | Owner |
|---|---|---|---|
| Q-1 | major | **FIXED** — #850 (ed172e43), PLAN-08; class-level `@BearerAuth` now read from the real bean class and merged with the method-level requirement (closed per PR; code not re-read) | PLAN-08 |
| Q-2 | major | **FIXED** — health checks by #795; both exception mappers now registered via build steps by #834 (ef720fd0), PLAN-04 (closed per PR; code not re-read) | PLAN-04 |
| Q-3 | major | **FIXED** — #834 (ef720fd0), PLAN-04; validator built eagerly at startup when at least one issuer is enabled (ADR 0019) (closed per PR; code not re-read) | PLAN-04 |
| Q-4 | major | **FIXED** — #834 (ef720fd0), PLAN-04; liveness catches and reports a resolver failure, stays UP (closed per PR; code not re-read) | PLAN-04 |
| Q-5 | minor | **FIXED** — #850 (ed172e43), PLAN-08; `tokenStore()`/`refreshScheduler()` producers are `@DefaultBean` (closed per PR; code not re-read) | PLAN-08 |
| Q-6 | minor | **FIXED** — #850 (ed172e43), PLAN-08; unknown default key, orphaned ancillary JWE properties and path-less `decryption-keys.<kid>` entries now fail startup (closed per PR; code not re-read) | PLAN-08 |
| Q-7 | minor | **FIXED** — #846 (17a3ef62), PLAN-12; `<scope>test</scope>` removed from the BOM's `jjwt-*` entries (closed per PR; code not re-read) | PLAN-12 |
| Q-8 | minor | **FIXED** — #834 (ef720fd0), PLAN-04; documented as RESTEasy Classic only, no Quarkus REST support added (closed per PR; code not re-read) | PLAN-04 |
| Q-9 | info | **FIXED** — #850 (ed172e43), PLAN-08; env-var-only issuers are discovered (anchor `SHERIFF_TOKEN_ISSUERS_<NAME>_ENABLED`; fix arm) (closed per PR; code not re-read) | PLAN-08 |
| Q-10 | major | **FIXED** — #846 (17a3ef62), PLAN-12; five concurrency benchmarks now `@State(Scope.Benchmark)` (closed per PR; code not re-read) | PLAN-12 |
| Q-11 | minor | **FIXED** — #846 (17a3ef62), PLAN-12; metrics exported once per trial under a per-method key (closed per PR; code not re-read) | PLAN-12 |
| Q-12 | minor | PARTIAL — pins/hardlink fixed (#678, #679); AGENTS.md residue remains (= D-5) | PLAN-19 |
| Q-13 | info | **FIXED** — #846 (17a3ef62), PLAN-12 (closed per PR; code not re-read) | PLAN-12 |

## Tests (TV / TC)

| ID | Sev | Verdict at HEAD | Owner |
|---|---|---|---|
| TV-1 | major | **FIXED** — misnamed test removed in #795; real confusion covered by `WiredNegativePathAttackTest` (unit gap → PLAN-15) | — |
| TV-2 | major | **FIXED** — #853 (a9ce05df), PLAN-11; parameterized ACCESS/ID x azp-situation test plus the audience-optional case, asserting `AUDIENCE_MISMATCH` and the counter (test names and assertions read in the merged source) | PLAN-11 |
| TV-3 | minor | **FIXED** — #795 | — |
| TV-4 | minor | PARTIAL — EventCategory ordinal removed; LoaderStatus/MeasurementType/EventCategory valueOf tests remain | PLAN-15 — NOT FIXED by operator decision (#867, 2026-11-06): the removals were flagged `weakened_test` and the three enum tests restored; trivial enum tests stay as a project convention |
| TV-5 | minor | **FIXED** — #795 | — |
| TV-6 | minor | VALID — tampered-payload tests fail at format, assert no event type | PLAN-15 — FIXED in #867 (`262369d6`, 2026-11-06) |
| TV-7 | info | VALID (intentional) — Javadoc note only | PLAN-15 — FIXED in #867 (`262369d6`): Javadoc note on `TestTokenHolder` |
| TV-8 | info | NO-ACTION — fixture self-tests; soft assertions acceptable for fixture checks | — |
| TC-1 | major | CHANGED — `RevocationSpecIT` replaced by `RefreshErrorPathSpecIT`; `invalid_grant` assertion residual | PLAN-16 — FIXED in #868 (`306e6053`, 2026-11-06): `RefreshErrorPathSpecIT` asserts `invalid_grant` |
| TC-2 | major | **FIXED** (CHANGED) — `RefreshProductionPathSpecIT` asserts rotation | — |
| TC-3 | minor | VALID — logout IT accepts error page | PLAN-16 — FIXED in #868 (`306e6053`): exact `Location` plus a negative case |
| TC-4 | minor | VALID — mix-up IT bypasses wired `CallbackHandler` | PLAN-16 — FIXED in #868 (`306e6053`): drives the wired `CallbackHandler` |
| TC-5 | minor | **FIXED** — #840 (a56b1b59), PLAN-06; pinned through `AuthorizationCodeFlow.exchange` (closed per PR; code not re-read) | PLAN-06 |
| TC-6 | minor | **FIXED** — #832 (31fb2212), PLAN-02; reflection removed, post-eviction replay pinned (closed per PR; code not re-read) | PLAN-02 |
| TC-7 | minor | **FIXED** — #862 (f29ca9cb), PLAN-14; valid-JSON adversarial token responses added in `TokenResponseAdversarialTest`, plus a repeated-member refusal in `TokenEndpointClient` (read in the merged diff) | PLAN-14 |
| TC-8 | minor | VALID — `ClientAuthSpecIT` positive-only | PLAN-16 — FIXED in #868 (`306e6053`): negatives added, with a production change (`ClientAuthenticationFailedException`) |
| TC-9 | info | PARTIAL — PKCE dup removed (#796); unit hygiene **FIXED** by #862 (f29ca9cb), PLAN-14 (`TokenEndpointTransportControlTest` moved, unused `@EnableGeneratorController` dropped; read in the merged diff); IT TLS trust-all → Watch | PLAN-14 |
| gaps VAL 1 | – | **FIXED** — = TV-2, #853 (a9ce05df), PLAN-11 | PLAN-11 |
| gaps VAL 2,3,5 | – | VALID — exp/nbf fixed-clock boundaries, unit RS/HS confusion | PLAN-15 — FIXED in #867 (`262369d6`) |
| gap VAL 4 | – | **FIXED** — #853 (a9ce05df), PLAN-11; new `StrictStringMapper` rejects any non-string `azp`; residual: `client_id` is still string-coerced by `IdentityMapper` (operator decision, see epic.md Watches) | PLAN-11 |
| gap VAL 6 | – | PARTIAL — dup-kid warning pinned (#803); winning key + counter unasserted | PLAN-15 — FIXED in #867 (`262369d6`) |
| gap VAL 7 | – | = TV-6 | PLAN-15 — FIXED in #867 (`262369d6`) |
| gap VAL 8 | – | = V-1 — **FIXED** by #835 (e010c5cf), PLAN-05 | PLAN-05 |
| gaps CLI 1,2 | – | = TC-5, TC-6 | PLAN-06, PLAN-02 |
| gaps CLI 3,4 | – | FIXED (4: `invalid_grant` residual = TC-1) | PLAN-16 |
| gaps CLI 7,8,9 | – | VALID — valid-JSON adversarial, discovery edges, iss/logout normalization | PLAN-14 |
| gaps IT 5,6,10 | – | VALID — = TC-3, TC-8, PKCE code injection at AS | PLAN-16 — FIXED in #868 (`306e6053`) |

## AsciiDoc (D)

| ID | Sev | Verdict at HEAD | Owner |
|---|---|---|---|
| D-1 | major | **FIXED** — root README #817; validation-quarkus README, health-checks and integration docs by #834 (ef720fd0), PLAN-04 (closed per PR; code not re-read) | PLAN-04 |
| D-2 | major | VALID — "no HTTP of its own" in 5 places | PLAN-18 |
| D-3 | major | **FIXED** — #861 (7145aebb), PLAN-13; `LogMessages.adoc` now lists `TokenSheriff_Q-025` and `TokenSheriff_Q-104` (read in the merged diff) | PLAN-13 |
| D-4 | major | **FIXED** — #861 (7145aebb), PLAN-13; the doc lists the duplicate-kid record as `TokenSheriff-171` and the new `TokenSheriff-172` (read in the merged diff) | PLAN-13 |
| D-5 | major | PARTIAL — = Q-12 residue in AGENTS.md | PLAN-19 |
| D-6 | major | VALID — stale "128 KB buffer" | PLAN-18 |
| D-7 | minor | VALID, broader — hub lists 1 of 15 ADRs; ADR-0002 "Proposed" | PLAN-19 |
| D-8 | minor | **FIXED** — #850 (ed172e43), PLAN-08; nonce-cache TTL default documented as 360 with the `proofMaxAge + 60` floor (closed per PR; code not re-read) | PLAN-08 |
| D-9 | minor | PARTIAL — range fixed (#805); CLIENT-23/24 unmapped | PLAN-18 |
| D-10 | minor | VALID — hub omissions, orphan benchmarking LogMessages | PLAN-19 |
| D-11 | minor | VALID, worse — 66 `<<…>>` refs in 12 files | PLAN-19 |
| D-12 | minor | VALID — "TokenSheriff" prose spelling | PLAN-19 |
| D-13 | minor | VALID — Quarkus version uncaveated | PLAN-18 |
| D-14 | info | VALID — introspection described, not implemented | PLAN-18 |
| D-15 | info | VALID — client-quarkus README; "cui-http-client" naming | PLAN-19 |

## Javadoc (J)

| ID | Sev | Verdict at HEAD | Owner |
|---|---|---|---|
| J-1 | major | VALID (example edited by #818, still broken) | PLAN-17 |
| J-2 | major | VALID | PLAN-17 |
| J-3 | major | VALID | PLAN-17 |
| J-4 | major | VALID | PLAN-17 |
| J-5 | major | VALID | PLAN-17 |
| J-6 | major | VALID | PLAN-17 |
| J-7 | major | VALID | PLAN-17 |
| J-8 | minor | **FIXED** — #850 (ed172e43), PLAN-08; `JwtPropertyKeys` Javadoc/config doc state the same (with D-8) (closed per PR; code not re-read) | PLAN-08 |
| J-9 | minor | VALID | PLAN-17 |
| J-10 | minor | VALID | PLAN-17 |
| J-11 | minor | VALID | PLAN-17 |
| J-12 | info | VALID | PLAN-17 |

## Tally

82 report findings: **6 FIXED/CHANGED-closed** (V-6, TV-1, TV-3, TV-5, TC-2, plus TC-1 reduced to a
residual), **1 NO-ACTION** (TV-8), **0 REFUTED**, remainder VALID or PARTIAL and owned by PLAN-01…PLAN-19.
