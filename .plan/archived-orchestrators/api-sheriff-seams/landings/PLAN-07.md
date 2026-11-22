# Landing Analysis: PLAN-07 — Public helper hygiene

epic: api-sheriff-seams
workstream: WS-02
pr: #908 (https://github.com/cuioss/TokenSheriff/pull/908), merged as 80bc2b73196ee75e05a55f58943c67f32a7fded0

> Landing record for one shipped plan. Lives at `landings/PLAN-07.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact.

Source: inbox message `plan-07-public-helper-hygiene-012.md` (lane `plan-marshall`, plan id `plan-07-public-helper-hygiene`), plus
eleven candidate-lesson messages (`-001` to `-011`). PR state read through `ci pr view` (`state: merged`, merge commit as above,
base `main`). `inbox landing-check`: `complete: false` (missing `unresolved_review_threads`, `review_body_findings_total`,
`review_body_findings_handled` and `sonar_open_new_issues`, all `unknown` in the landing); `surface_delta` is `unmeasured`
(neither the declared nor the realized surface was supplied), so the declared-vs-realized comparison below was made by hand from
the 69-file merge diff (+7181/-2479).

## Deliverable Fidelity vs Spec

The spec lists seven deliverables (five at staging, two folded in on the operator's word after the PLAN-05 and PLAN-06
landings); the landing reports `deliverables_total=15`, `deliverables_done=15`, which is the plan's own outline numbering and is
not visible in the spec (the outline was not read). Each of the spec's seven traces to code and to a test that can fail, with the
gaps below. The leaf compared test-method counts per split family against `1be3867b` and the orchestrator re-counted four of the
six families (`@Test`, `@ParameterizedTest`, `@RepeatedTest` annotations): logout 51 to 35 + 16 (+13 new in `NumericDateTest`),
signature-only 24 to 17 + 7, health check 18 to 10 + 8 (+6 new in `IssuerStatusFailureTest`), opt-in resolver 20 to 16 + 5, so the
opt-in family gained one. Nothing was lost in any family. Parameterized case counts were not compared.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. Public `Bearer` token extractor | shipped-as-specified | `BearerCredentials` (`commons.http`); the producer uses it and its private extractor and `BEARER_PREFIX` are deleted. The old call site's blank-cookie guard was dropped and restored as `isBlank` after review (`BearerTokenProducer:340`, leaf read; CodeRabbit thread `bd25ca`, re-read). Scheme case and whitespace decisions OD-1 and OD-2 are itemised in ADR-0031 (re-read). |
| 2. Public `insufficient_scope` challenge builder | shipped-as-specified, with one request-path consequence the docs do not state (L3-2) | `BearerChallenge`, used by `BearerTokenResponseFactory`; `InsufficientScopeChallengeRoundTripTest` runs it against the client parser. A scope outside the RFC 6749 §3.3 grammar now throws `IllegalArgumentException` from `build()` (OD-3). |
| 3. `ErrorTextSanitizer` printable-ASCII escape mode | shipped-as-specified | `escapeNonPrintableAscii` and `ErrorTextSanitizerPrintableAsciiTest`. OD-4 (the backslash is escaped, which API Sheriff's helper does not do) is recorded in ADR-0031 but not flagged as an API Sheriff consumer impact (L1-3). |
| 4. One public bounded body handler | shipped-as-specified | `BoundedBodyHandler` replaces `BoundedBodyHandlers` and the client `BoundedContentBodyHandler`, both deleted; the three callers are migrated. The client back channel moves from a `ByteArrayOutputStream` (re-read at `1be3867b`) to the validation handler's per-chunk list (L3-4). |
| 5. Documentation, traceability and decision record | shipped, with stale hub text (L2-1, L2-2, L2-3) | ADR-0031 plus edits to ADR-0022, -0029, -0030; `VALIDATION-14`, `COMMONS-16`, `COMMONS-17`; transport and error-model specifications; `test-impl-mapping.adoc` rows for the new classes (leaf read; the orchestrator did not enumerate them). `doc/commons/README.adoc:53` still says `COMMONS-1`–`COMMONS-15` and `doc/README.adoc` stops at ADR-0029 (both re-read). |
| 6. Back-channel logout validator hardening | shipped-as-specified, one criterion not met as written (L1-2) | `numericDate` bounded before scaling for both exponent signs (`LogoutTokenValidator:371-388`, re-read), `MAX_FRESHNESS_WINDOW = Duration.ofDays(36_525)` (`:112`, re-read), `SignatureVerifiedToken.toString` keeps `typ` out. Both health-check catches route through one sanitising method (re-read); six classes split. |
| 7. Unknown-`kid` refresh and initial-load retry hardening | shipped-as-specified, with the guard-before-window trade-off kept on purpose (L3-1, L3-3) | The hand-off is wrapped, the guard is released on any `RuntimeException` from `execute()` (`HttpJwksLoader:879-889`, re-read); window failures name the Quarkus key for the window (`IssuerConfigResolver:584-590`, re-read); `HttpJwksLoaderUnknownKidGapsTest` and `HttpJwksLoaderExecutorHandOffTest` cover the lane's test gaps (leaf read). The `initialLoadRetry` plus refresh-interval-0 combination still fails in the builder, not with the Quarkus key (L2-4). |

Added beyond the spec's list, explained: `UncheckedThrowBoundaryTest` (allow-list entries for the new throw sites),
`LogRecordSanitizationGuardTest` (the new `typ` guard), `WellKnownResultConverterTest` (a comment rename), and
`LogoutTokenValidator` swapping `Objects.requireNonNullElse(rawToken, "")` for a ternary (behaviour-neutral, no deliverable
names it). Declared and not touched, correctly: `BearerTokenProducerTest`, `HttpJwksLoaderDefaultBehaviourUnchangedTest`,
`ObservedValidatorResolverTest` (the leaf confirmed each is absent from the diff).

## Three-Layer Analysis

A level-3 read-only leaf produced the first pass from the spec and the narrative as scratchpad copies (it opened no `.plan/`
path); the orchestrator re-read the points below at HEAD `80bc2b73` before recording them, and dropped one (see L1-1). The leaf
disclosed two deviations from its read-only limit: it wrote one scratch script (`cmp_tests.py`, in the scratchpad; it only runs
`git show`) and one `grep -rl` over the repo root listed paths under `.plan/` without reading them. Not verified by anyone: every
build and test result (the lane claims 5145 tests green), CI, the integration tests, coverage, whether the "promptly" tests fail
on the old code, the spray-rate magnitude of the guard pulse, the JDK client's chunk granularity and its handling of a malformed
`Content-Length`.

### Layer 1 — fidelity

- **L1-1 (dropped) "Spec premise refuted on `fromIssuerConfig:208`".** The leaf read the spec's "logs the issuer identifier and the
  loader message without `ErrorTextSanitizer`, as `fromIssuerConfig:208` does" as saying `fromIssuerConfig` was already clean. Both
  catches were raw at `1be3867b` (`JwksEndpointHealthCheck:208` and `:227`, re-read), and the sentence reads naturally as "also
  unsanitised". Not a premise error; the sentence is ambiguous. The landing fixed both sites where the spec's test criterion named
  one, which is consistent with its aim.
- **L1-2 (info) The 16-digit-mantissa criterion cannot hold as written.** The spec says a 16-digit mantissa with a large exponent
  is refused by the owning check. The merged test pins `SIGNATURE`, because the token parser's digit limit refuses it first
  (`LogoutTokenValidatorNumericDateTest:108-120`, re-read; the parser limit itself was not re-read, the test comment states it).
  The other adversarial inputs reach `numericDate` and assert `ISSUED_AT` or `EXPIRATION`.
- **L1-3 (info) OD-4 is a consumer-visible difference for API Sheriff.** ADR-0031 records that the backslash is escaped; API Sheriff's
  `renderForMessage` leaves it unescaped, while the hex case matches. The spec's premise that API Sheriff can delete its copy with no
  behaviour change holds only after this. (Leaf read of ADR-0031; the orchestrator did not re-read API Sheriff's helper.) Routed to the
  re-measure watch.

### Layer 2 — implemented

- **L2-1 (minor) `doc/commons/README.adoc:53` says `COMMONS-1`–`COMMONS-15`.** `COMMONS-16` and `COMMONS-17` exist in
  `requirements.adoc` (re-read).
- **L2-2 (minor) `doc/README.adoc` lists ADRs only up to 0029.** ADR-0030 (PLAN-06) and ADR-0031 are absent from the hub index
  (re-read). PLAN-06 left the first gap.
- **L2-3 (minor) The landing's "behaviour changes to defaults itemised in ADR-0031" is only partly borne out.** The PR body lists the
  100-year `MAX_FRESHNESS_WINDOW` under that heading, but ADR-0031's "Changed default behaviour" does not hold it (it is in ADR-0029 and
  `security-reference.adoc`, re-read). Not itemised there: the unchanged-JWKS discard moving from WARN to DEBUG (ADR-0030 only, leaf read),
  the health-check WARN no longer carrying a stack trace and now carrying a sanitised issuer, and the client body handler's switch to a
  chunk list.
- **L2-4 (minor) The `initialLoadRetry` plus refresh-interval-0 failure still names the builder, not the Quarkus key.**
  `IssuerConfigResolver` Javadoc at `:600-603` says the combination "is validated by the builder" (re-read); only the unknown-kid window has
  its own key-naming check (`:584-590`). The sibling is asymmetric and ADR-0030 does not record that as intended.
- **L2-5 (minor) `JwksEndpointHealthCheckIssuerStatusFailureTest` cannot detect loss of issuer sanitisation.** `ISSUER` is the benign
  `failing-issuer` (`:56`); only the exception message is hostile (`:57`), and the assertions at `:137-148` would stay green if the issuer
  went through raw (re-read). The spec's criterion asked the logged text, including the issuer, to be asserted. The PR body itself notes
  there is no forged-identifier health-check test.
- **L2-6 (minor, advice only) The opt-in resolver split also added a test (20 to 21).** `shouldPassWindowWithPositiveRefreshInterval` is
  a positive control for the new refresh-0 check; AGENTS.md advises a split to be a pure move, and the class is not on the spec's split list.
- **L2-7 (info, not shown by reading)** Whether the "promptly" tests discriminate (a 10 s bound against a minutes-long expansion; the
  "valid token not blocked" control is scheduling-dependent) needs a mutation run. The ADR-0029 revision-history convention check (spec
  6(c)) has no recorded outcome. The `test-impl-mapping.adoc` rows, `DocumentedPackagePatternTest`, `check-javadocs-pre-commit` and the
  coverage figures are run-only.

### Layer 3 — correct

No blocker and no major defect found by reading. Five minors (reported to the operator in the same turn, none staged; see Routing):

- **L3-1 (minor) A forged-`kid` miss that loses the window still takes the shared in-flight guard first, so periodic cycles and their
  `maxStaleness` check can be skipped.** `HttpJwksLoader.scheduleUnknownKidRefresh:862-875` takes `fetchInFlight` before the window check
  and releases it when the window is not open; `performBackgroundRefresh:1476-1479` skips its cycle, uncounted and without the staleness
  evaluation, whenever it meets that moment (re-read). ADR-0030 and `openid-discovery.adoc` state the guard is taken "when a window is
  claimed" and that only a sustained deferral needs a multi-thread executor and a slow identity provider. The code takes it for every miss.
  The duty cycle per request is tiny and the lane knew the order (operator-chosen, CodeRabbit finding `92a11a08`, declined on its thread
  with that reason, re-read); what is unstated is that a spray at request rate pulses it. Magnitude not shown without a run.
- **L3-2 (minor) A `@BearerAuth(requiredScopes=...)` value outside the scope grammar, or more than 64 scopes, now gives an
  exception from the scope-violation path instead of a 401 challenge.** `BearerTokenResponseFactory.createConstraintViolationResponse:158-163`
  passes the annotation's scopes to `BearerChallenge...build()`, whose `IllegalArgumentException` is not caught;
  `BearerTokenInterceptor.validateBearerToken:164-167` calls `createErrorResponse()` with no catch and `BearerTokenResult.createErrorResponse`
  documents only `IllegalStateException` (all re-read). No startup validation of annotation scope names was found by grep in the Quarkus module.
  Trigger: a scope such as `"read write"`, plus a request with a valid token missing that scope. The effect is an unhandled exception on the
  request thread (an HTTP 500 by reading; not run). ADR-0031 itemises the `IllegalArgumentException` but not this path or the absent startup check.
- **L3-3 (minor) A failed unknown-`kid` hand-off still burns the window.** `nextAllowedNanos` is advanced (`HttpJwksLoader:873-875`) before
  `execute` and the catch releases only the guard (`:884-889`, re-read). The next unknown-`kid` fetch is delayed up to the window (at most 3600 s).
  The new test only passes because it advances the clock by the window; ADR-0030 and the config Javadoc do not say so.
- **L3-4 (minor) The memory bound holds in bytes and not in objects, and the client back channel now carries that.** `BoundedSubscriber` keeps
  `List<byte[]> chunks` with one array per delivered buffer (`BoundedBodyHandler:259`, re-read); the deleted client handler used a single
  `ByteArrayOutputStream` (re-read at `1be3867b`). `COMMONS-17` and the transport specification state the byte bound only. This is the
  structure CodeRabbit (`2d376536`) proposed replacing; the replacement was applied, found to break the documented bound, reverted, and the
  thread was declined and accepted by the bot, with "a body of one-byte chunks is a follow-up candidate". Whether the JDK client delivers
  one-byte buffers is not shown by reading.
- **L3-5 (minor) `BearerChallenge.Builder` accumulates before it refuses.** `scopes(Collection)` adds every valid scope to a `TreeSet`
  (`:160-168`) and the 64-scope and 8192-character checks run in `build()` (`:179-198`, re-read); `realm` and `errorDescription` are not
  length-bounded. Caller-controlled input only, so the effect is an oversized allocation before a refusal.
- **L3-6 (not shown by reading)** `firstValueAsLong("Content-Length")` (`BoundedBodyHandler:154`) throws `NumberFormatException` on a non-numeric value,
  outside any try; whether the JDK client has already rejected such a header, and how the exception surfaces, was not shown. No test covers it.

Confirmed correct by reading (leaf, with the `numericDate` bound, the guard release and the extraction guards re-read): the `numericDate` bound for both
exponent signs and the sub-nanosecond branch; the saturating freshness window; `BearerCredentials` (scheme case, blank, tab, internal space, control
characters; `toString` never prints the token); the hand-off guard release; `ErrorTextSanitizer.escapeNonPrintableAscii` (surrogates per half, DEL, C1,
U+2028/2029, bidi, backslash). `escapeNonPrintableAscii` uses `String.format` per escaped character and has no ceiling on `maxLength` (info).

### Conformance with the project's rules

| Rule source | Verdict | Evidence |
|-------------|---------|----------|
| AGENTS.md: Javadoc with `@since`, thread-safety, example on public types | deviates (minor) | `BoundedBodyHandler`'s public nested types `OverLimitPolicy` and `BoundedBody` and the `MAX_CEILING` constant carry no `@since` (leaf read). The PR states `@since 1.0` is used on purpose. |
| AGENTS.md: test classes at or under 500 lines; splits are pure moves | deviates (advice) | New and split classes are 134-497 lines (leaf); one split added a test (L2-6). |
| AGENTS.md: no magic numbers | deviates (minor) | `BearerCredentials` (`0x20`) and `BearerChallenge` (`0x20`, `0x21`, `0x23`, `0x5B`, `0x5D`, `0x7E`) use commented grammar literals (leaf). |
| AGENTS.md and CLAUDE.md: requirement and ADR traceability, documentation consistency | deviates | L2-1, L2-2. |
| AGENTS.md: statements traced to the enforcing code | deviates | L3-1 (guard taken per miss) and L3-4 (bytes, not objects). |
| AGENTS.md: LogRecord ranges and `doc/LogMessages.adoc` | conforms | No new `LogRecord`; the row for the unchanged-discard case was updated (leaf). |
| AGENTS.md: pre-1.0, no deprecation shims | conforms | Both old handlers, the producer extractor and `buildInsufficientScopeHeader` are deleted (leaf). |
| AGENTS.md: adjacent `cui-rewrite:disable` placement | conforms | `HttpJwksLoader:884` (re-read) and `:963`. |
| JSpecify: `@NullMarked`, package-info for a new package | conforms | `commons.http/package-info.java` (leaf). |
| Spec Standing Constraints: native image | conforms | No reflect-config change and no new dependency (leaf, from the diff). |

Layer verdicts: layer 1 sound with three minor notes (L1-2 and L1-3 are informational); layer 2 carries minors only, mostly in hub docs and one test that cannot fail on
the property it names; layer 3 carries five minor defects and one point not shown by reading, none blocking.

## Metrics and Anomalies

- Tokens: 29,447,488 total (lane report; per-phase split not in the payload). Wall-clock 35,880 s (about 10.0 h). Model `claude-sonnet-5-5`,
  effort `medium`, harness `claude`, plugin and executor `0.1.2133`. The lane could not re-measure the model at landing (`reason: model_unknown`); both
  values are self-reported start-time values.
- The merge diff is 69 files (+7181/-2479) against a spec of seven deliverables; the spec's own scope-bloat note kept it whole on a recorded rationale.
- The lane's finalize steps all read `done` (15 steps; `emit-landing` and `archive-plan` had no recorded outcome when the payload was assembled).
  `functional-review` and `code-review` both closed by operator override (`acceptance=operator_override`, six findings accepted each), no verdict or count facts recorded. `automatic-review` recorded one
  comment refused-structural with unified triage pending at its last firing (the lane's statement; not re-checked).
- Cost the lane reports, not recounted: five fix commits after the PR opened (`94b98aec`, `323601d9`, `5e179faa`, `cd8a95c6`, `a243b688`), each re-staling both
  reviews and the pre-push gate; two `-Ppre-commit` runs rewrote imports in files the plan did not touch, and the reverted churn changed the tree sha; the six accepted
  `weakened_test` split flags were re-raised at every later head (lessons already filed or recorded, see Routing).

## Routing and Merge Behavior

- Review: bots `coderabbitai`; the PR carries seven inline threads, all `resolved` (re-read from `ci pr comments`): three fixed (`be6a7494` ADR clock advice, `562b761e`
  blank cookie, `3f1f4826` pickup bound), three declined with a reason (`601693dc` header-length constant, `946e40f5` `error_description` grammar, `92a11a08` guard order) and one
  declined after a reverted fix (`2d376536`). CodeRabbit did not mark the header-length and the chunk-buffer findings addressed; its own comments name the header-length round-trip test and the
  one-byte-chunk case as the open follow-ups. `ci pr comments` reports `unresolved: 4` of 21, which are non-thread rows; every thread row (17 rows over seven threads) reads `resolved=true`.
- Provider re-reads on the merged head: `runbook_lane review-gate --pr-number 908` returned `unresolved_review_threads: 0`, `review_body_findings_total: 7`, `review_body_findings_handled: 0`,
  `merge_allowed: false`, `reason: body_findings_undisposed`. The landing's four keys are all `unknown`, so none is contradicted (`unknown` asserts only that nothing was observed). The `handled` figure
  is not reproducible by the provider and the plan has no readable progress file here, so it is **not re-verified**, and the provider's `0` is a parser reading, not a statement that the seven were ignored (each thread carries a reply).
  Sonar read: `count_status: undecidable` (`head_revision_unavailable`; analysis revision `a243b688`), recorded as unknown, never 0. The lane's `step.sonar-roundtrip.new_code_issue_count=0` counts issues without
  security hotspots, as it says.
- Merge: through the merge queue (`step.branch-cleanup.merge_mechanism=merge_queue`); `cleanup_owed=false`. The CI run failed once on an unrelated e2e browser flake and passed on rerun (lane's statement, decision-log line `22:04:49Z`; not re-checked).
  The lane names only "an unrelated e2e browser flake"; whether it is the `accessibility-wcag-compliance.spec.js:60` flake already recorded is not stated, and the orchestrator did not check.
- Collisions: `doc/developer/test-impl-mapping.adoc` was edited by this merge (+27/-15) and PLAN-04's merge did not touch it (watch narrowed to PLAN-07 earlier); `doc/LogMessages.adoc` was edited here (2 lines)
  after PLAN-04's rows, and the merge diff shows the `TokenSheriff-173` row updated for the DEBUG-only unchanged case. The landing does not say whether a conflict occurred, and nothing read here shows one.

## Reconciliation Actions

- Queue row `PLAN-07`: `running` to `shipped`; `pr` `#908`; `landing` `landings/PLAN-07.md`; `plan_marshall_plan_id` `plan-07-public-helper-hygiene` (the lane's own sender id; the archived plan directory carries the same name).
- Moved anchors for the next plans: `PLAN-03` and `PLAN-08` now start from `HttpJwksLoaderConfig` and `IssuerConfigResolver` as changed here (the config builder methods, the window check at `IssuerConfigResolver:584-590`), and
  `BoundedBodyHandler` replaced both old handlers; `BoundedBodyHandlers` and `BoundedContentBodyHandler` no longer exist.
- API Sheriff must re-measure (added to the epic watch): the Bearer scheme case and whitespace rules (OD-1, OD-2), a token with an internal space or control character answered 400, a scope outside the grammar refused, the
  backslash escaping in the sanitiser (OD-4) and in the privileges header, the 100-year freshness window, the unchanged-JWKS discard now DEBUG, and the client back channel's `Content-Length` pre-check and new failure text.
- Open Defects added: the incomplete landing (four required keys `unknown`); the PLAN-07 layer-3 and layer-2 residuals (not staged). Lessons: `2026-11-16-06-001` and `2026-11-16-06-002` filed; the other nine candidates folded into existing items.
