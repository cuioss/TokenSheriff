# PLAN-28: Epic cleanup sweep — carry every open defect and watch that needs repository work

epic: quality-report
workstream: WS-05

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-28-epic-cleanup-sweep.md` and is queued as one row file, `queue/PLAN-28.json`.
> The orchestrator EMITS the command below; it never launches the plan inline.
> Staged 2026-11-10 on the operator's word ("gather all leftovers ... and watches and put them into a cleanup plan; after #26
> lands, add corresponding leftovers as well"). Grounded at `aeb86e4d`; deliverable 7 was filled on 2026-11-10 after PLAN-26 (#888,
> `7520e165`) landed and was analyzed, and its entries are grounded at `7520e165`. Emit only on the operator's word.

## Objective

Close the repository-side work the epic still carries as Open Defects and Watches after PLAN-27: the DPoP replay-cache and
log-sanitisation defects, the documentation PLAN-27 shipped wrong, the test pins and verifications PLAN-25 left without
evidence, the native-image coverage of class-level `@BearerAuth`, the e2e teardown hang and its screenshot flake, and what
PLAN-26 left behind (its minor parser looseness, test gaps and the shared sanitiser). Every item is verified at outline against the tree first and then fixed, pinned by a test that
fails before and passes after, or closed with the line that settles it. Fail-closed: a fix never widens what a token or a
proof may do.

## Deliverables

1. **DPoP replay cache and proof logging (epic Open Defect, PLAN-25 layer 3)** — (a) the replay cache never evicts an in-window
   jti, so it exceeds `maxSize` by as many entries as arrive within one TTL window. DECIDED by the operator 2026-11-10: KEEP the
   soft cap and DOCUMENT it. No hard cap and no refusal of new proofs; an in-window jti is never evicted (that would permit a replay).
   Document the behaviour and its bound (arrival rate x `nonceCacheTtlSeconds`, reclaimed by the periodic `evictExpired` sweep) in
   the `DpopConfig` Javadoc for `nonceCacheSize` and in `doc/validation/threat-model.adoc` row DP1 (line 325), which today calls it a
   "bounded cache" and gives a 300 s TTL although `DEFAULT_NONCE_CACHE_TTL_SECONDS` is 360: correct both, and pin
   it with a test that fills the cache with in-window jtis past `maxSize` and shows that none is forgotten (a replay of each is
   still refused) and that the entries are reclaimed once their window closes; (b) every insert past `maxSize` scans the whole map even when nothing is evictable: make the insert
   cost not grow with the cache while nothing is evictable, without ever forgetting an in-window jti; (c) `jti`, `htm` and `htu`
   reach `LOGGER.warn` unsanitised in `DpopProofValidator` (three call sites): route them through the project's sanitiser and
   name `jti`, `htm`, `htu` in `LogRecordSanitizationGuardTest` so the guard sees them, with a test that fails if a call site
   regresses. Done when each fix has a test that fails on the unfixed code.
2. **Documentation PLAN-27 shipped wrong (epic Open Defect)** — (a) `AGENTS.md` § Test Class Size "Current state" states figures
   that are false at `aeb86e4d` (30 classes, largest 1024 lines; measured: 39 above 500 over all `src/test` Java, 34 `*Test.java`,
   largest `NonValidatingJwtParserTest` at 1023): either state a measured figure with the command that produced it or drop the
   line, since a count rots; (b) `CLAUDE.md` § "Maven Build Gotcha" says a reactor `test-compile`/`test` "always" fails and names two
   consumers of the `validation:generators` test jar while five poms carry the classifier, and `AGENTS.md` still tells agents to run
   `./mvnw test`: settle the claim with a real run, then make `AGENTS.md` the single owner of build commands and align or remove
   the `CLAUDE.md` note (evidence 2026-11-10, a lane claim from the PLAN-26 PR body: a whole-tree `test-compile` and `test` failed
   identically on `main` and on the branch with 20 identical errors, "package `de.cuioss.sheriff.token.validation.test` does not exist"
   in `benchmark-core` and client test sources, while `verify -Ppre-commit` ran all 4287 tests green; it supports the `CLAUDE.md`
   claim but is not a run by the orchestrator, so the real run here still settles it); (c) `security-reference.adoc` § Claims Validation says "no later step can tell the two apart" for a null
   and an omitted member, which holds for top-level members only (a nested null is kept): say so. Done when a reader following
   `AGENTS.md` and `CLAUDE.md` gets the same build commands from both.
3. **Test pins and gaps left without evidence (PLAN-27 L2-3, PLAN-25 4(b), 10(c), 10(d))** — (a) an omitted optional identifier
   claim (`jti`, `client_id`) is ACCEPTED: first read whether a test already pins it, else add one assertion (the
   `IdentifierClaimNullEquivalenceTest` pin is relative only); (b) `PsychicSignatureAttackTest`: establish by a scratch run that
   removing the explicit ECDSA range check makes it fail, record the result, or remove the row; (c) `TokenValidatorHealthCheck` /
   `JwksEndpointHealthCheck` catch-suppression placement and whether `TokenValidatorStartupInitializerTest` pins the proxy
   dereference; (d) `JweDecryptorTest` (or its split classes) rows for a null or blank `epk` coordinate, `apu`/`apv` decryption,
   an inner-JWS payload decode failure and the unsupported-compression case, and `token-decryption.adoc` line 200, which
   over-generalises the `kty` rejection. Each is read first and either fixed or dropped with the line that closes it.
4. **Verify-or-soften and record (PLAN-25 7(l)(5), 7(l)(6), 10(h), beyond-spec narrowings)** — (a) ADR-0026 says an
   application-supplied `@Alternative TokenValidator` bypasses the startup check: verify with a run (`ObservedValidatorResolver`
   may resolve the handles lazily and throw outside startup) or soften the sentence; (b) run the project's AsciiDoc/xref check
   once over `doc/` and the module READMEs (including the `xref:#id[]` form) and record the result; fix a broken target; (c) render
   `client-auth-code-pkce.svg` once and fix what is wrong; (d) the non-JSON Content-Type refusal at token, PAR and userinfo
   (`RedeemedResponseException`) and the "`expires_in` must be a JSON number" rule were beyond the PLAN-25 spec: read the
   #883/#884 PR bodies and the docs, and state each narrowing in the client threat model or specification if it is not already.
5. **Native-image coverage of class-level `@BearerAuth` (PLAN-25 item 1, security-relevant)** — an integration endpoint carrying a
   class-level `@BearerAuth` and no `@RegisterForReflection`, with a `BearerAuthSpecIT` case proving an unsatisfied class
   requirement is refused and a satisfied one passes, run in the native container; settle whether the class-level lookup needs
   native reflection metadata and whether `BearerTokenInterceptor` can fail open when ArC reports an unannotated generated override
   (a fail-open is fixed with a test that fails before and passes after); exercise `StrictStringMapper` once in the native run
   (its `reflect-config.json` entry was never exercised natively); record whether the CI runner can run the native build.
   If the native run cannot be made here, record the evidence and the owner and change nothing else.
6. **e2e teardown hang and screenshot flake (epic Watch, PLAN-24 #876, PLAN-13, #883 queue ejection)** — the cause of the
   `e2e-tests` 30-minute timeout was never named (PLAN-25 shipped a hardened `stop-dev-environment.sh` and waived the criterion to
   CI): read the logs of the cancelled and ejected runs through `ci checks` / `ci-artifacts`, name what blocks, fix it in this
   repository with the smallest change, or record the evidence and owner (the jobs call reusable workflows in `cuioss-organization`)
   and change nothing here; and the Playwright screenshot comparison in an accessibility spec that ejected #883 once: find the
   spec, make it deterministic or record the flake handling. Never mark the job non-required to pass a gate. RECURRENCE 2026-11-10
   (PLAN-26 landing, #888): the `e2e-tests` job hung for 30 minutes on head `4c19ef2d` and was cancelled with an empty log, AFTER
   PLAN-25's hardened `stop-dev-environment.sh` was on `main`; the operator-approved re-run passed in 240 s (the passing run is the one
   `ci checks status 888` shows; the hung run is a lane claim, not visible there). So the hardened script did not cure it: look for
   what happens before teardown, and for the empty log (a job that produced no output at all).
7. **Carried from PLAN-26 (`landings/PLAN-26.md`, #888 merged as `7520e165`, analyzed 2026-11-10)** — every point is minor, was found
   by reading and re-read by the orchestrator at its line; each is verified at outline first, then fixed with a test that fails
   before, or dropped with the line that closes it. A fix never widens what a challenge may carry.
   (a) **Strict-mode looseness (L3-1).** `BearerChallengeParams.readValue` (`:207-210`) accepts an UNQUOTED value unless it holds a
   quote or a backslash, so `Bearer error="insufficient_scope", scope=read write` parses as two scope tokens, although RFC 6750 makes
   `scope` a quoted string. Decide by reading RFC 6750 section 3: refuse an unquoted `scope` in STRICT mode (fail-closed, with a test
   that fails before), or state the leniency in `CLIENT-25` (`doc/client/requirements.adoc`, whose bullet list omits it today).
   (b) **Unicode-blank scope (L3-2).** `InsufficientScopeChallengeParser.parseScopes` (`:112`) tests `scope.isBlank()`, which is
   Unicode-aware: `scope="<U+2003>"` yields a challenge with an EMPTY scope set, while the same character beside a token is refused
   by the ASCII grammar. Make the two consistent (an ASCII-only blank test, or refuse) and pin it.
   (c) **LENIENT `unquote` (L3-3).** `acr_values="a" Basic realm="x"` unquotes to the value `a" Basic realm="x` in the step-up parser
   (`BearerChallengeParams.java:219-237`, inherited from before the extraction), and the Javadoc at `:214-218` says a value that
   never closes its quote is returned unchanged although an escaped final quote is stripped: correct the Javadoc, and read
   `StepUpResultVerifier` (`:106-126`, how the value is split into tokens) to settle whether the pollution matters; refuse it in the
   sibling only if it can.
   (d) **Test gaps found in the landed tests (L2-3, L2-4, L3-5, L3-6, timing, the PR's follow-ups).** (i) the statelessness test
   (`InsufficientScopeChallengeParserTest.java:365-387`) cannot catch a static mutable field or a race: assert there is none, or
   add a concurrent call; (ii) the OWASP and ModSecurity tests in `InsufficientScopeChallengeAttackDatabaseTest` (`:126-138`) feed
   the payload as the whole header and are refused by the scheme check alone: embed the payload where it reaches the guards (inside
   `error_description`, `realm` and `scope`) or say they only cover the scheme check; (iii) point (f) of
   `JwksLoaderKeyIdsContractTest` (`:203-218`) does not fail for a live view over the OLD loader's map and has no file or in-memory
   case: add the case or state the limit in the class comment; (iv) the second assertion of point (c) (`:153-154`,
   `getKeyInfo(BLANK_KID).isEmpty()`) can never fail because `JWKSKeyLoader.getKeyInfo` returns empty for a blank kid: drop or
   replace it; (v) points (g) and (h) depend on a 1 s refresh, a 3 s grace period and a 10 s await (`:79-81`), and (g) asserts
   `getKeyInfo(FIRST_KID).isPresent()` right after the await, inside the 3 s grace: widen the grace or restructure so a CI stall
   cannot fail it; (vi) `InsufficientScopeChallengeParserTest.java:223` holds a raw U+2028 in a string literal: use the ` `
   escape.
   (e) **The shared sanitiser (L3-4).** `ErrorTextSanitizer.sanitize` cuts at 256 characters with `substring(0, 256)` (`:89`), which
   can split a surrogate pair, and its control set (`isControlCharacter`: below 0x20, DEL and the C1 controls) does not include the
   bidirectional controls U+202A to U+202E and U+2066 to U+2069. It serves every consumer, so verify first, then fix it once with a
   test, or record why it is left; this is pre-existing and was found by the analysis, not introduced by PLAN-26.
   (f) **Public-API drift from the PLAN-26 spec (L2-2, L2-4).** The spec and the PR body call `InsufficientScopeChallengeParser` and
   its record "the plan's only new public API", but `INSUFFICIENT_SCOPE` and `MAX_SCOPE_TOKENS` are public constants
   (`InsufficientScopeChallengeParser.java:76,79`, the second referenced by `{@value #MAX_SCOPE_TOKENS}` in the Javadoc at `:89`; the
   sibling exposes its error constant too). Decide: keep them public and name them in `CLIENT-25` and the class Javadoc as API, or
   narrow them to package-private (nothing outside the module reads them today: confirm with `git grep` first, since a public
   constant removed is a break). Also read `InsufficientScopeChallengeSpecIT` (extends `BaseIntegrationTest`, drives no Keycloak flow,
   named in no PLAN-26 deliverable): keep it as the per-requirement IT the `test-strategy.adoc` convention asks for, or record why
   it is unnecessary; no change if it is cheap and the convention requires it.
   Done when each fix has a test that fails on the unfixed code and each dropped point names the line that closes it.

Scope: seven items, above the ~6 guard. Rationale: the operator asked for one cleanup plan; the items are independent, so the plan
lifecycle may cut at outline along (1), (2)+(4), (3), (5), (6), (7) exactly as PLAN-25 was cut into two PRs. Record the cut.

## Standing Constraints

- Fail-closed: no fix widens what a token, proof or request may do; a doc correction states the behaviour the code has, read from
  the code, never the reverse.
- Every behaviour fix carries a test that fails on the unfixed code (a scratch run recorded in the PR body).
- A claim in this spec is verified at outline; a refuted claim is corrected in the plan, not carried.
- No test class is split and none is grown past 500 lines without the reason in the PR body (`AGENTS.md` § Test Class Size).
- Where the project analyses its pull requests with Sonar (`resolve` reports `sonar: configured`): an open Sonar issue is fixed in a
  pushed commit, or dismissed with a stated reason recorded as `{issue-key}: {reason}` and transmitted through
  `plan-marshall:workflow-integration-sonar:sonar_rest transition --issue-key {key} --transition {falsepositive|wontfix}`; the merge
  gate reads the open-issue count at the head (`unknown` for a failed read, never `0`).

## Claim Labels

- OBSERVED: `DpopReplayProtection.evictOldest` evicts only entries whose `ttlMillis` window has elapsed and its comment states the
  cache "may temporarily exceed maxSize" under a flood of in-window jtis — read at `DpopReplayProtection.java` § `evictOldest`
  (aeb86e4d); the growth is therefore bounded by arrival rate x TTL, not unbounded (this corrects the 2026-11-09 Open Defect wording)
- OBSERVED: `checkAndStore` calls `evictOldest()` after every successful insert while `seenJti.size() > maxSize`, and `evictOldest`
  streams the whole map, filters to expired entries, and only then sorts, so with nothing evictable the cost is one full scan per
  insert, not a sort of the whole map (this corrects the 2026-11-09 Open Defect wording) — `DpopReplayProtection.java` § `checkAndStore`, `evictOldest`
- OBSERVED: a periodic `evictExpired` sweep is scheduled at fixed rate — `DpopReplayProtection.java:74,106`
- OBSERVED: `DpopConfig.java:35` documents `nonceCacheSize` as "Maximum number of jti entries" and `:60,:69` default it to 10_000 entries and 360 s,
  while `doc/validation/threat-model.adoc:325` says 300 s and "bounded cache": both overstate the bound the soft cap gives
- OBSERVED: `DpopProofValidator.java:351` passes `jti`, `:441` passes `htm.get()` and `:457` passes `htu.get()` as raw arguments to
  `LOGGER.warn` (`DPOP_REPLAY_DETECTED`, `DPOP_HTM_MISMATCH`, `DPOP_HTU_MISMATCH`)
- HYPOTHESIS: nothing downstream of those call sites sanitises the arguments (CWE-117) — confirm/refute at
  `JWTValidationLogMessages` § the three `DPOP_*` records and the project's sanitiser (verify-at-outline)
- OBSERVED: `LogRecordSanitizationGuardTest.REMOTE_TEXT_NAMES` is `kid, kty, keyId, keyType, alg, algorithm, crv, actual, getMessage`
  and `EXEMPT` is empty — `LogRecordSanitizationGuardTest.java:58-64`; `jti`, `htm`, `htu` are not named
- OBSERVED: `AGENTS.md:133` says 30 classes above 500 lines and a largest of 1024; measured at `aeb86e4d` by a read-only newline count
  over tracked `src/test` Java: 39 above 500 (34 `*Test.java`, 31 in validation and client), `NonValidatingJwtParserTest` 1023,
  `AccessTokenCacheTest` 1000
- OBSERVED: `CLAUDE.md:26-34` says `mvnw test-compile` / `mvnw test` "always" fail; `AGENTS.md:82,151` tell agents to run `./mvnw test`;
  `<classifier>generators</classifier>` appears in five poms (`token-sheriff-validation`, `token-sheriff-validation-quarkus`,
  `token-sheriff-quarkus-integration-tests`, `token-sheriff-client`, `benchmark-core`)
- HYPOTHESIS: a reactor `mvnw test-compile` fails on `main` for the stated reason — confirm/refute by running it at outline
  (the orchestrator did not run a build)
- OBSERVED: `security-reference.adoc:34` reads "no later step can tell the two apart"; `MapRepresentation.fromJson` filters only
  top-level null members (lines 100-118), and the PLAN-27 PR states a nested null is still kept
- OBSERVED: `IdentifierClaimNullEquivalenceTest.java:170-185` compares null against omitted only; a keyword grep of
  `token-sheriff-validation/src/test` found no test that an omitted `jti`/`client_id` is accepted (a lead, not a finding)
- HYPOTHESIS: items 3(b) to 3(d), 4(a) to 4(d), 5 and 6 are carried from the PLAN-25 spec text and the PLAN-25 landing's
  "no evidence" list; their files exist at `aeb86e4d` (path sweep) but their behaviour claims were not re-read — each is
  confirmed or refuted at outline in the files named in Expected Surface
- HYPOTHESIS: the "docs-only pull request skips the Sonar build" behaviour (PLAN-19 residue) lives in the reusable workflow
  `cuioss/cuioss-organization` `reusable-maven-build.yml` (`maven.yml:22` only calls it), so it is not repository work and is NOT
  carried here — confirm at `.github/workflows/maven.yml` (verify-at-outline)
- OBSERVED: `BearerChallengeParams.readValue` returns an unquoted value unchanged in STRICT mode unless it contains a quote or a
  backslash (`BearerChallengeParams.java:203-212`, merged `7520e165`); `CLIENT-25` lists the refusals and does not name this one
  (`doc/client/requirements.adoc`, the bullet list under `[#CLIENT-25]`)
- OBSERVED: `InsufficientScopeChallengeParser.parseScopes` treats `scope.isBlank()` as an empty scope set (`:110-114`), and
  `isScopeToken` is ASCII-only (`:138-147`), so a Unicode-whitespace-only scope and the same character beside a token differ
- OBSERVED: the LENIENT `unquote` strips the surrounding quotes whenever the first and last character are `"` (`BearerChallengeParams.java:219-237`),
  so `"a" Basic realm="x"` becomes `a" Basic realm="x`; its Javadoc (`:214-218`) says a value that never closes its quote is
  returned unchanged
- HYPOTHESIS: `StepUpResultVerifier` splits the required value on spaces and needs the ID token's `acr` to be among the tokens
  (`StepUpResultVerifier.java:106-126`; only the final `contains` at `:126` was read) — confirm at outline
- OBSERVED: `ErrorTextSanitizer.sanitize` cuts with `value.substring(0, MAX_LENGTH)` (`:89`) and its `isControlCharacter` is
  `c < 0x20 || (c >= DELETE && c <= LAST_C1_CONTROL)`, so U+202A to U+202E and U+2066 to U+2069 are not escaped;
  `client/internal/LogSanitizer.sanitize` delegates to it (`LogSanitizer.java:68-69`)
- OBSERVED: `JwksLoaderKeyIdsContractTest` uses a 1 s refresh interval, a 3 s grace period and a 10 s await (`:79-81`), and the
  rotation swaps in a NEW `JWKSKeyLoader` (`HttpJwksLoader.java:936`), so a live view over the old loader's map is not caught by (f)
- OBSERVED: `InsufficientScopeChallengeParserTest.java:223` holds a raw U+2028 inside a string literal (`cat -A` shows the bytes
  E2 80 A8); the statelessness tests are `:365-387`
- HYPOTHESIS: the PLAN-26 e2e hang (a 30-minute `e2e-tests` job on `4c19ef2d`, cancelled with an empty log) is a lane claim from
  the landing message; `ci checks status 888` shows only the passing re-run (240 s) — confirm or refute from the cancelled run's log
- Verify-first clause: every HYPOTHESIS above and the whole Expected Surface are re-verified against HEAD at outline before scoping;
  a refuted claim loops back to re-scope.

## Expected Surface

- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/dpop/DpopReplayProtection.java` (deliverable 1)
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/dpop/DpopProofValidator.java` (deliverable 1)
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/dpop/DpopConfig.java` (deliverable 1a: the `nonceCacheSize` Javadoc, lines 35 and 97, documents the soft cap)
- OBSERVED: `doc/validation/threat-model.adoc` (deliverable 1a: row DP1 at line 325 says "bounded cache (default 10,000 entries, 300s TTL)"; `DpopConfig.java:69` sets `DEFAULT_NONCE_CACHE_TTL_SECONDS = 360`)
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/LogRecordSanitizationGuardTest.java` (deliverable 1)
- HYPOTHESIS: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/dpop/` (deliverable 1: the replay and proof-validator tests; verify-at-outline)
- OBSERVED: `AGENTS.md` (deliverable 2)
- OBSERVED: `CLAUDE.md` (deliverable 2)
- OBSERVED: `doc/validation/security-reference.adoc` (deliverable 2)
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/pipeline/IdentifierClaimNullEquivalenceTest.java` (deliverable 3a)
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/security/PsychicSignatureAttackTest.java` (deliverable 3b)
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/SignatureVerificationUtil.java` (deliverable 3b: scratch run only, not edited)
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/producer/TokenValidatorStartupInitializerTest.java` (deliverable 3c)
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/health/JwksEndpointHealthCheck.java` (deliverable 3c)
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/health/TokenValidatorHealthCheck.java` (deliverable 3c)
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwe/` (deliverable 3d: `JweDecryptorTest` and its split classes)
- OBSERVED: `doc/validation/specification/token-decryption.adoc` (deliverable 3d: line 200)
- OBSERVED: `doc/adr/0026-Orphaned_or_unresolvable_extension_configuration_fails_startup_instead_of_being_ignored.adoc` (deliverable 4a)
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/observability/ObservedValidatorResolver.java` (deliverable 4a: read, edited only if the verification shows a defect)
- OBSERVED: `doc/resources/diagrams/client-auth-code-pkce.svg` (deliverable 4c)
- HYPOTHESIS: `doc/client/threat-model.adoc` (deliverable 4d; verify-at-outline)
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/interceptor/BearerTokenInterceptor.java` (deliverable 5)
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/test/java/de/cuioss/sheriff/token/integration/bearer/BearerAuthSpecIT.java` (deliverable 5)
- HYPOTHESIS: `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/main/java/` (deliverable 5: the new class-level `@BearerAuth` endpoint; verify-at-outline)
- OBSERVED: `token-sheriff-validation/src/main/resources/META-INF/native-image/de.cuioss.sheriff.token/token-sheriff-validation/reflect-config.json` (deliverable 5: only if native evidence demands a change)
- OBSERVED: `token-sheriff-quarkus-parent/e-2-e-playwright/scripts/stop-dev-environment.sh` (deliverable 6)
- HYPOTHESIS: `token-sheriff-quarkus-parent/e-2-e-playwright/tests/accessibility-wcag-compliance.spec.js` (deliverable 6: the spec holding the screenshot comparison that ejected #883; the file exists, which spec flaked is not confirmed; verify-at-outline)
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/scripts/stop-integration-container.sh` (deliverable 6)
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/scripts/lib-docker-compose.sh` (deliverable 6)
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/BearerChallengeParams.java` (deliverable 7a, 7c)
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/InsufficientScopeChallengeParser.java` (deliverable 7b)
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/StepUpResultVerifier.java` (deliverable 7c: read; edited only if the pollution matters)
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/InsufficientScopeChallengeParserTest.java` (deliverable 7b, 7d; 389 lines, so at most 111 lines may be added without the reason in the PR body)
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/InsufficientScopeChallengeAttackDatabaseTest.java` (deliverable 7d)
- HYPOTHESIS: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/StepUpChallengeParserTest.java` (deliverable 7c: a row, only if the sibling must refuse the polluted value; 340 lines; verify-at-outline)
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwks/JwksLoaderKeyIdsContractTest.java` (deliverable 7d; 310 lines)
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/error/ErrorTextSanitizer.java` (deliverable 7e)
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/commons/error/ErrorTextSanitizerTest.java` (deliverable 7e)
- OBSERVED: `doc/client/requirements.adoc` (deliverable 7a: the `CLIENT-25` bullet list)

Read, not edited: `.github/workflows/maven.yml`, `e2e-tests.yml`, `integration-tests.yml` (they call reusable workflows in
`cuioss-organization`), `doc/` and the module READMEs for the AsciiDoc link check (a broken target is fixed where it is found).
Deliverable 7's surface (the PLAN-26 files) is declared above; `StepUpChallengeParser.java` is read only, and the `CLIENT-12` step-up
text in `doc/client/specification/step-up-and-logout.adoc` already states the three tightenings (lines 99 and 115-116).

## Dependencies and Sequencing

- Depends on: PLAN-26, which SHIPPED 2026-11-10 (#888, `7520e165`) and was analyzed (`landings/PLAN-26.md`); deliverable 7 now
  carries its leftovers. Nothing else is running, so no overlap prompt applies; the plan may be emitted on the operator's word.
- Overlaps with: none. `doc/client/threat-model.adoc` (4d) was edited by PLAN-26 and is on `main` already.
- Adjacent to: the upstream-owned items stay Watches and are NOT carried (see below).

## Not carried (stay Watches, with the reason)

- Upstream (plan-marshall): the unstamped `effort` residue, the PLAN-27 incomplete landing facts, the lesson-routing gap, the nine
  unsent addenda, the CodeRabbit quota and review-barrier items, the merge-message summary block.
- Operator-owned and held for a release: the API Sheriff reply and release note, the `registry_parity` readiness, stale branches,
  Dependabot PR #872, GitHub #358 and #182 (non-goals).
- Not repository work: the docs-only Sonar skip (reusable workflow in `cuioss-organization`, HYPOTHESIS above).
- Deliberately not staged since 2026-10-31: TC-9 (a real truststore for the Keycloak container in the integration tests, IT-cost). Add
  it as an eighth item only on the operator's word.
- Informational: the PLAN-25 spec-premise note, the `source-report/` line numbers.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/quality-report/plans/PLAN-28-epic-cleanup-sweep.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates and edits NO file under
`.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
