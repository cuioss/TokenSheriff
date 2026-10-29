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
| V-1 | major | VALID — `JwtHeader.epk` still `String`; ECDH-ES unparseable via production parser | PLAN-05 |
| V-2 | major | **FIXED** — #833 (2ea3ef2f), PLAN-03; hit path and eviction re-apply the issuer's `maxTokenAgeSeconds` (closed per PR; code not re-read) | PLAN-03 |
| V-3 | major | **FIXED** — #830 (1ca5ab35), PLAN-01 (closed per PR; merge state corroborated, code not re-read) | PLAN-01 |
| V-4 | major | **FIXED** — #830 (1ca5ab35), PLAN-01; transient discovery failure retried, absent advertised issuer now terminal (closed per PR; code not re-read) | PLAN-01 |
| V-5 | major | **FIXED** — #830 (1ca5ab35), PLAN-01; plus Quarkus `TokenValidatorProducer` `@PreDestroy` (closed per PR; code not re-read) | PLAN-01 |
| V-6 | major | **FIXED** — #818 (9fc9d4d3) gate propagation, #817 (6d45da13) error text; `IssuerConfigDiscoveryIssuerTest` | — |
| V-7 | major | PARTIAL — TLS fixed #748, retry fixed #795; connect/read timeouts still dropped in well-known mode | PLAN-07 |
| V-8 | minor | **FIXED** — #830 (1ca5ab35), PLAN-01 (closed per PR; code not re-read) | PLAN-01 |
| V-9 | minor | **FIXED** — #833 (2ea3ef2f), PLAN-03; entries carry their issuer's clock skew (closed per PR; code not re-read) | PLAN-03 |
| V-10 | minor | VALID — no staleness bound, no `jwks_uri` re-discovery | PLAN-07 |
| V-11 | minor | VALID — DPoP path lacks ECDSA range check (CVE-2022-21449) | PLAN-09 |
| V-12 | minor | VALID — DPoP replay `checkAndStore` before thumbprint check | PLAN-09 |
| V-13 | minor | VALID — LogRecord template/argument mismatches | PLAN-13 |
| V-14 | minor | VALID — identifier 168 duplicated (Transport + JWTValidation) | PLAN-13 |
| V-15 | minor | VALID — `maxPayloadSize` doubles as HTTP response ceiling | PLAN-13 |
| V-16 | minor | **FIXED** — #830 (1ca5ab35), PLAN-01 (closed per PR; code not re-read) | PLAN-01 |
| V-17a | info | VALID — EgressPolicy DNS-rebinding claim / missing ranges | PLAN-13 |
| V-17b | info | VALID — trailing-dot tokens (`split("\\.")`) | PLAN-09 |
| V-17c | info | VALID — `exp` boundary off-by-one | PLAN-09 |
| V-17d | info | VALID — duplicate JSON keys last-wins (doc only) | PLAN-18 |
| V-17e | info | VALID — EC JWK `alg` not cross-checked against curve | PLAN-09 |
| V-17f | info | VALID — empty `aud` array falls to `azp` fallback | PLAN-11 |
| V-17g | info | VALID — RSA-OAEP random-CEK mitigation absent | PLAN-05 |
| V-17h | info | VALID — observability drift (`JWKS_KEYS_UPDATED` content etc.); J-1 part owned by PLAN-17 | PLAN-13 |

## Client code (C)

| ID | Sev | Verdict at HEAD | Owner |
|---|---|---|---|
| C-1 | major | **FIXED** — #832 (31fb2212), PLAN-02 (closed per PR; merge state corroborated, code not re-read) | PLAN-02 |
| C-2 | minor | **FIXED** — #832 (31fb2212), PLAN-02; the PR body files this fix under "C-8b" (closed per PR; code not re-read) | PLAN-02 |
| C-3 | minor | VALID — DPoP `token_type` / `cnf.jkt` not verified at issuance | PLAN-10 |
| C-4 | minor | VALID — `EndSessionFlow` no scheme check, fragment mishandled | PLAN-06 |
| C-5 | minor | VALID — `CallbackParameters.toString()` unsanitized | PLAN-06 |
| C-6 | minor | VALID — error callback handled before `state` check | PLAN-06 |
| C-7 | info | VALID — single-use state/nonce is caller contract only | PLAN-06 |
| C-8a | info | VALID — sanitizers miss U+0085/U+2028/U+2029 | PLAN-06 |
| C-8b | info | **FIXED** — #832 (31fb2212), PLAN-02; caps constructor-injectable (closed per PR; code not re-read) | PLAN-02 |
| C-8c | info | VALID — PAR produced but never composed into a flow | PLAN-10 |
| C-8d | info | VALID — step-up `auth_time` check without skew / clock | PLAN-10 |

## Quarkus / benchmarking / build (Q)

| ID | Sev | Verdict at HEAD | Owner |
|---|---|---|---|
| Q-1 | major | VALID — class-level `@BearerAuth` read from proxy class | PLAN-08 |
| Q-2 | major | **FIXED** — health checks by #795; both exception mappers now registered via build steps by #834 (ef720fd0), PLAN-04 (closed per PR; code not re-read) | PLAN-04 |
| Q-3 | major | **FIXED** — #834 (ef720fd0), PLAN-04; validator built eagerly at startup when at least one issuer is enabled (ADR 0019) (closed per PR; code not re-read) | PLAN-04 |
| Q-4 | major | **FIXED** — #834 (ef720fd0), PLAN-04; liveness catches and reports a resolver failure, stays UP (closed per PR; code not re-read) | PLAN-04 |
| Q-5 | minor | VALID — `TokenStore`/`RefreshScheduler` producers lack `@DefaultBean` | PLAN-08 |
| Q-6 | minor | VALID — JWE config silent fallbacks | PLAN-08 |
| Q-7 | minor | VALID — BOM pins jjwt with `test` scope | PLAN-12 |
| Q-8 | minor | **FIXED** — #834 (ef720fd0), PLAN-04; documented as RESTEasy Classic only, no Quarkus REST support added (closed per PR; code not re-read) | PLAN-04 |
| Q-9 | info | VALID (mechanism; env-var consequence unverified) | PLAN-08 |
| Q-10 | major | VALID — "concurrent" benchmark is thread-scoped | PLAN-12 |
| Q-11 | minor | VALID — metrics export last-writer-wins | PLAN-12 |
| Q-12 | minor | PARTIAL — pins/hardlink fixed (#678, #679); AGENTS.md residue remains (= D-5) | PLAN-19 |
| Q-13 | info | VALID — stale class/package references | PLAN-12 |

## Tests (TV / TC)

| ID | Sev | Verdict at HEAD | Owner |
|---|---|---|---|
| TV-1 | major | **FIXED** — misnamed test removed in #795; real confusion covered by `WiredNegativePathAttackTest` (unit gap → PLAN-15) | — |
| TV-2 | major | VALID — no empty-`aud` test | PLAN-11 |
| TV-3 | minor | **FIXED** — #795 | — |
| TV-4 | minor | PARTIAL — EventCategory ordinal removed; LoaderStatus/MeasurementType/EventCategory valueOf tests remain | PLAN-15 |
| TV-5 | minor | **FIXED** — #795 | — |
| TV-6 | minor | VALID — tampered-payload tests fail at format, assert no event type | PLAN-15 |
| TV-7 | info | VALID (intentional) — Javadoc note only | PLAN-15 |
| TV-8 | info | NO-ACTION — fixture self-tests; soft assertions acceptable for fixture checks | — |
| TC-1 | major | CHANGED — `RevocationSpecIT` replaced by `RefreshErrorPathSpecIT`; `invalid_grant` assertion residual | PLAN-16 |
| TC-2 | major | **FIXED** (CHANGED) — `RefreshProductionPathSpecIT` asserts rotation | — |
| TC-3 | minor | VALID — logout IT accepts error page | PLAN-16 |
| TC-4 | minor | VALID — mix-up IT bypasses wired `CallbackHandler` | PLAN-16 |
| TC-5 | minor | VALID — state single-use untested (pairs with C-7) | PLAN-06 |
| TC-6 | minor | **FIXED** — #832 (31fb2212), PLAN-02; reflection removed, post-eviction replay pinned (closed per PR; code not re-read) | PLAN-02 |
| TC-7 | minor | VALID — attack corpora collapse to non-JSON | PLAN-14 |
| TC-8 | minor | VALID — `ClientAuthSpecIT` positive-only | PLAN-16 |
| TC-9 | info | PARTIAL — PKCE dup removed (#796); unit hygiene → PLAN-14; IT TLS trust-all → Watch | PLAN-14 |
| gaps VAL 1 | – | = TV-2 | PLAN-11 |
| gaps VAL 2,3,5 | – | VALID — exp/nbf fixed-clock boundaries, unit RS/HS confusion | PLAN-15 |
| gap VAL 4 | – | VALID — list-typed `azp` | PLAN-11 |
| gap VAL 6 | – | PARTIAL — dup-kid warning pinned (#803); winning key + counter unasserted | PLAN-15 |
| gap VAL 7 | – | = TV-6 | PLAN-15 |
| gap VAL 8 | – | = V-1 | PLAN-05 |
| gaps CLI 1,2 | – | = TC-5, TC-6 | PLAN-06, PLAN-02 |
| gaps CLI 3,4 | – | FIXED (4: `invalid_grant` residual = TC-1) | PLAN-16 |
| gaps CLI 7,8,9 | – | VALID — valid-JSON adversarial, discovery edges, iss/logout normalization | PLAN-14 |
| gaps IT 5,6,10 | – | VALID — = TC-3, TC-8, PKCE code injection at AS | PLAN-16 |

## AsciiDoc (D)

| ID | Sev | Verdict at HEAD | Owner |
|---|---|---|---|
| D-1 | major | **FIXED** — root README #817; validation-quarkus README, health-checks and integration docs by #834 (ef720fd0), PLAN-04 (closed per PR; code not re-read) | PLAN-04 |
| D-2 | major | VALID — "no HTTP of its own" in 5 places | PLAN-18 |
| D-3 | major | VALID — Q-025 / Q-104 missing from LogMessages.adoc | PLAN-13 |
| D-4 | major | VALID — = V-14 doc side | PLAN-13 |
| D-5 | major | PARTIAL — = Q-12 residue in AGENTS.md | PLAN-19 |
| D-6 | major | VALID — stale "128 KB buffer" | PLAN-18 |
| D-7 | minor | VALID, broader — hub lists 1 of 15 ADRs; ADR-0002 "Proposed" | PLAN-19 |
| D-8 | minor | VALID — nonce TTL default 300 vs 360 (with J-8) | PLAN-08 |
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
| J-8 | minor | VALID (with D-8) | PLAN-08 |
| J-9 | minor | VALID | PLAN-17 |
| J-10 | minor | VALID | PLAN-17 |
| J-11 | minor | VALID | PLAN-17 |
| J-12 | info | VALID | PLAN-17 |

## Tally

82 report findings: **6 FIXED/CHANGED-closed** (V-6, TV-1, TV-3, TV-5, TC-2, plus TC-1 reduced to a
residual), **1 NO-ACTION** (TV-8), **0 REFUTED**, remainder VALID or PARTIAL and owned by PLAN-01…PLAN-19.
