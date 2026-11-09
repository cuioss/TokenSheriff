# PLAN-26: API Sheriff downstream requests — the `getKeyIds()` contract test and the `insufficient_scope` challenge parser

epic: quality-report
workstream: WS-01

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Staged 2026-11-09 at the operator's instruction ("spec a test"), from the API Sheriff downstream request
> (`api-sheriff-kidicap-gateway-requirements-001`, observed at `f85c5ab3`; the release information in its part A was
> declined by the operator). The same day the operator asked for the `insufficient_scope` parser (part C) to be in the SAME
> plan ("make it one plan for both"), so this spec carries two deliverables. The file name and the queue slug
> (`jwks-key-ids-contract-pin`) are those of the first deliverable and are historical: a queue row's slug cannot be edited,
> and the plan is identified by `PLAN-26` and this title. The orchestrator EMITS the command below; it never launches the plan
> inline.

## Objective

API Sheriff's next plan replaces its approximate JWKS refresh rule with one that compares the key ids of the fresh
and the current active key set, and so depends on `JwksLoader.getKeyIds()` as a documented contract. The contract is
written in the interface Javadoc and is already pinned in several places, but only piecemeal and mostly below the
public types: the blank-id and duplicate-id cases are asserted on `JWKSKeyLoader` only, and nothing asserts the contract
once, in one place, through the three public entry points (`JwksLoaderFactory.createHttpLoader`, `createFileLoader`,
`createInMemoryLoader`). This plan adds one contract test class that states each point of the contract and runs it against
every loader type the factory returns, so a downstream consumer reads one document and a regression in any
implementation fails one named test. Test-only: no production change, no new behaviour, nothing widens.

The same consumer's gateway must answer an upstream `403` with `WWW-Authenticate: Bearer error="insufficient_scope"`
(RFC 6750 section 3.1) by refreshing or widening the grant, and needs the challenge parsed. TokenSheriff already owns the
sibling RFC 9470 parser, `StepUpChallengeParser`, which deliberately returns nothing for any other error, so the operator
decided that the `insufficient_scope` parser belongs in `token-sheriff-client` and not in the gateway: header parsing for a
security decision is an attack surface (PLAN-14 lesson 004, PLAN-25 7(g)), and one tested parser should serve every consumer.
This is the plan's only new public API; it parses and returns a value, acts on nothing, and every uncertain input fails
closed to an empty result.

## Deliverables

1. **`JwksLoader` key-id contract test** — one new test class (name by role, `JwksLoaderKeyIdsContractTest`, in
   `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwks/`) with one test per contract point,
   each run against the loader types the factory creates (HTTP, file, in-memory) wherever the point applies. The points
   are the interface Javadoc's own words (`JwksLoader.java` lines 66-78, `HttpJwksLoader.java` lines 728-745,
   `JWKSKeyLoader.java` lines 300-319), one verification criterion each:
   (a) never `null`, and empty before initialisation (HTTP: also empty after a first load that yielded no usable key);
   (b) the set holds exactly the ids of the active key set, and every reported id resolves through `getKeyInfo`;
   (c) a blank or whitespace-only id is omitted (HTTP loader: through a JWKS served by the mock dispatcher; file and
   in-memory: through the content);
   (d) a duplicate id is reported once, and the later key shadows the earlier one in `getKeyInfo`;
   (e) the returned set is immutable (`UnsupportedOperationException` on `add` and `remove`);
   (f) a snapshot already returned is not changed by a later load or refresh (HTTP: drive one rotation with
   `performBackgroundRefresh`, as `HttpJwksLoaderKeyRotationTest` line 176 does);
   (g) key sets retired in a grace period are excluded from the snapshot while `getKeyInfo` still resolves their id within
   the period, and neither after it (HTTP only);
   (h) `getLoaderStatus() == OK` only while a usable key set is installed, and a JWKS with no usable key never replaces an
   installed set (HTTP only; the contract's status half that API Sheriff reads).
   Where an existing test already asserts a point at the same level (`JWKSKeyLoaderTest` lines 172-282 for (a) to (e) on
   `JWKSKeyLoader`; `HttpJwksLoaderKeyRotationTest` lines 176-190 for (f) and (g); `HttpJwksLoaderFailureTest` "Status
   reflects usable keys" for (h)), the new test does NOT copy the setup: it asserts the point through the factory entry
   point and names the existing test in a comment, so the class is the index of the contract and not a second copy of the
   suites. A point found unpinned at the factory level is the plan's finding; a point found false is stopped and reported,
   never "fixed" by editing the Javadoc to match.
   **Non-vacuity (required):** for each of (b) to (g), the run removes or weakens the one production line the point rests
   on in a scratch run (for example the blank filter in `JWKSKeyLoader.getKeyIds`, the retired-set exclusion in
   `HttpJwksLoader.getKeyIds`, the `Set.copyOf`-style immutability), records that the new test fails, then restores the line;
   the mutation is never committed. The PR body lists the point, the mutated line and the failing test.
2. **RFC 6750 `insufficient_scope` challenge parser in `token-sheriff-client`** — a new public class in the `flow` package,
   sibling of `StepUpChallengeParser` (name by role, `InsufficientScopeChallengeParser`), with
   `Optional<InsufficientScopeChallenge> parse(@Nullable String wwwAuthenticate)` and a record
   `InsufficientScopeChallenge(Set<String> scopes, @Nullable String realm, @Nullable String errorDescription)` (the record's
   `scopes` an immutable, order-preserving set; the exact component names and accessor style follow `StepUpChallenge`, outline
   decides). The consumer-visible field and edge set, one verification criterion each:
   (a) only a challenge whose scheme is `Bearer` (case-insensitive, as the sibling) and whose `error` is exactly
   `insufficient_scope` parses; any other error, scheme or shape is `Optional.empty()`; `null` and blank are empty;
   (b) `scope` is split on single spaces; every token must match the RFC 6749 section 3.3 scope-token grammar (`%x21 /
   %x23-5B / %x5D-7E`: no space, double quote or backslash, no control character, no non-ASCII), a token outside the grammar
   rejects the whole challenge, an empty token from repeated spaces is dropped, and a duplicate token is reported once;
   (c) an absent or whitespace-only `scope` yields a challenge with an EMPTY scope set (the RFC makes the attribute optional;
   the consumer sees "scope refused, none named" and decides) — the outline default; the alternative (`Optional.empty()`) is
   chosen only if reading `StepUpChallengeParser`'s blank handling (lines 76-86, "fail closed instead") shows the same
   reasoning applies, and the choice is recorded in the PR body;
   (d) `realm` and `error_description` are optional; `error_description` is sanitised (`ErrorTextSanitizer`/`LogSanitizer`:
   control characters escaped, length bounded) before it leaves the parser and is never logged raw;
   (e) a duplicate `auth-param` name rejects the whole challenge (as the sibling does), so an injected second `scope` or
   `error` cannot win; a quoted value that contains `", error="` or a comma stays inside its value;
   (f) the header-level attack class, enumerated once as a parameterised test in the `cui-http-testing` attack-database
   pattern PLAN-25 7(g) names: CR, LF, CRLF, obs-fold continuation (a line break followed by SP or HT), an embedded NUL and any
   other control character anywhere in the value yield empty; an unterminated quote and a trailing backslash yield empty or the
   sibling's documented verbatim behaviour, never an exception;
   (g) bounds so a hostile header cannot make the parser do unbounded work: a stated maximum header length (default 8 KiB) and a
   maximum token count (default 64) yield empty beyond them; the figures are an outline decision, recorded;
   (h) a value that carries more than one challenge (`Bearer ...` followed by another scheme after a comma, or another scheme
   first) must not leak the other challenge's parameters into the Bearer result: only a value that STARTS with the Bearer
   scheme is parsed, and the parameters of a following scheme are never read as Bearer parameters (read how the shared
   splitter treats `Basic realm="x"` after a comma and fail closed when it is ambiguous);
   (i) returns a value and acts on nothing: no refresh, no scope widening, no I/O, no state; the class is thread-safe by being
   stateless.
   **One splitter, not two:** do not copy the quoted-string aware `auth-param` splitting and `unquote` of `StepUpChallengeParser`
   (lines 100-180) a second time. Extract them into one package-private helper used by both parsers, behaviour-preserving for
   the sibling and guarded by the unchanged `StepUpChallengeParserTest` (227 lines, twelve tests). A tightening of the sibling
   that the shared header checks bring (control characters, size) is a fail-closed change, stated in the PR body and given its
   own test; nothing may start to accept what the sibling rejects today.
   **Documentation (same change):** a new requirement `CLIENT-25` in `doc/client/requirements.adoc` (the next free id; the file
   ends at CLIENT-24) with its README mapping row (`doc/client/README.adoc`) and traceability row, the parser described next to
   the RFC 9470 text in `doc/client/specification/step-up-and-logout.adoc`, a threat-model row for header injection and scope
   smuggling in `doc/client/threat-model.adoc`, and a Javadoc with `@since 1.0`, a usage example that compiles and a statement of
   what the parser does NOT do. The class is plain Java with no reflection, so no `reflect-config.json` entry is expected;
   confirm that and say so in the PR body.
   **Tests:** `InsufficientScopeChallengeParserTest` (new, at or under 500 lines; split the attack matrix into its own class
   if it would exceed that) covering (a) to (i), each able to fail: for (b), (e), (f) and (h) remove the guarding line in a
   scratch run, record that the test fails, restore it, and list the points in the PR body as for deliverable 1.
   **Not in this deliverable:** acting on the challenge (refresh or widening is the consumer's), parsing `invalid_token` or
   `invalid_request` challenges, a general `Bearer` challenge parser type, a change to `StepUpHandler`, and any HTTP call.

## Standing Constraints

- Deliverable 1 is test-only: no production line changes and no change to the `JwksLoader` Javadoc unless a point is found
  false (then stop and report). Deliverable 2 adds one public class, one small package-private helper and documentation; it
  changes no existing behaviour except a stated fail-closed tightening of `StepUpChallengeParser`. Neither adds a `LogRecord`
  (the sibling logs at debug with `CuiLogger`; follow it) or touches `doc/LogMessages.adoc`.
- JUnit 5 and the CUI test conventions already used in the neighbouring jwks tests (`cui-test-generator`, the module
  dispatcher and `InMemoryJWKSFactory` helpers, `@DisplayName` per test); build and verify via Maven only.
- **Test size (operator, 2026-11-07, a guideline): more than 500 lines should be avoided.** The new class stays at or under
  500 lines; if the matrix does not fit, split by loader type into a small abstract contract and one subclass per type, not by
  weakening a point.
- Fail-closed posture is untouched; state in the PR body that nothing here changes acceptance.
- Tool findings (Sonar, OpenRewrite markers) are fixed immediately; suppression only in the rule's sanctioned syntax with a
  stated reason. SonarCloud analyses PRs (`sonar: configured`): an open Sonar issue is fixed in a pushed commit, or dismissed
  with a stated reason recorded as `{issue-key}: {reason}` and transmitted through
  `plan-marshall:workflow-integration-sonar:sonar_rest transition --issue-key {key} --transition {falsepositive|wontfix}`.
  The merge gate reads the open-issue count at the head: `0` confirmed or every remaining issue dismissed; an `undecidable`
  read blocks. Read the count with a branch- or PR-scoped `resolved=false` filter, never an unfiltered issue search.
- Every claim below was grounded at `f85c5ab3` (the ledger landing on `main`; the validation sources are those of
  `d1af3ebb`). Re-verify each against the implementing source at outline before scoping on it.
- Fail-closed for deliverable 2: an unparseable, ambiguous or oversized header is `Optional.empty()`, never a partial result,
  and a new public type is listed in the PR body as the plan's only API addition.
- The plan creates and edits no file under `.plan/orchestrator/` other than its own inbox message (Write-Boundary).

## Claim Labels

- OBSERVED: `JwksLoader.getKeyIds()` is documented as an immutable snapshot of the active key set, empty before loading, never `null`, with retired grace-period sets excluded while `getKeyInfo` may still resolve their ids — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/JwksLoader.java` lines 66-78
  - verdict: corroborated | checked_at: f85c5ab3 | by: quality-report/analyze | rescoped: n/a | evidence: JwksLoader.java:66-78 documents immutable active-set snapshot, empty before load, never null, retired sets excluded
- OBSERVED: `JWKSKeyLoader.getKeyIds()` returns `Set.of()` until initialised and otherwise an unmodifiable set of the key map's non-blank ids; `HttpJwksLoader.getKeyIds()` reads `currentKeys` once and delegates, `Set.of()` before the first successful load — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/key/JWKSKeyLoader.java` lines 311-319 and `.../jwks/http/HttpJwksLoader.java` lines 739-745
  - verdict: corroborated | checked_at: f85c5ab3 | by: quality-report/analyze | rescoped: n/a | evidence: JWKSKeyLoader.java:311-319 Set.of() until initialised then non-blank ids; HttpJwksLoader.java:739-745 reads currentKeys once and delegates
- OBSERVED: `JWKSKeyLoaderTest` pins every point on `JWKSKeyLoader` — all keys reported (method at 174), unmodifiable (186), empty before init (196), duplicate once with the WARN (208), blank omitted with every reported id resolvable (268) — read at `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwks/key/JWKSKeyLoaderTest.java` lines 172-282
  - verdict: corroborated | checked_at: f85c5ab3 | by: quality-report/analyze | rescoped: n/a | evidence: JWKSKeyLoaderTest methods at 174, 186, 196, 208, 268 read
- OBSERVED: `HttpJwksLoaderKeyRotationTest` pins, at the HTTP loader, that after a rotation `getKeyIds()` is exactly the rotated id, the retired id still resolves through `getKeyInfo` in the grace period, and a snapshot taken before the rotation is unchanged — read at `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwks/http/HttpJwksLoaderKeyRotationTest.java` lines 176-190
  - verdict: corroborated | checked_at: f85c5ab3 | by: quality-report/analyze | rescoped: n/a | evidence: HttpJwksLoaderKeyRotationTest:176-190 read: rotated id only, retired id resolves via getKeyInfo, earlier snapshot unchanged
- OBSERVED: `HttpJwksLoaderFailureTest` pins the status contract ("Status reflects usable keys": a first load with no usable key is neither `OK` nor a key installation, with and without background refresh) and the `HttpJwksLoaderAsyncInitializationTest` asserts empty ids before the keys are installed — read at `.../jwks/http/HttpJwksLoaderFailureTest.java` lines 316-400 and `.../HttpJwksLoaderAsyncInitializationTest.java` lines 224-291
  - verdict: corroborated | checked_at: f85c5ab3 | by: quality-report/analyze | rescoped: n/a | evidence: HttpJwksLoaderFailureTest 'Status reflects usable keys' (316-398) and Async test asserts at 230, 291 found by grep; bodies not read line by line
- OBSERVED: no test in `FileJwksLoaderTest`, `InMemoryJwksLoaderTest` or `JwksLoaderFactoryTest` asserts `getKeyIds()` (the factory test asserts `LoaderStatus.OK` for a valid file and valid content only), and no test asserts blank-id omission or duplicate-id collapse through `HttpJwksLoader` (the only HTTP-level `getKeyIds` size assertion is the CR/LF key id case at `HttpJwksLoaderTest.java` lines 283-285) — read by `git grep getKeyIds` over `token-sheriff-validation/src/test/java/.../jwks`
  - verdict: corroborated | checked_at: f85c5ab3 | by: quality-report/analyze | rescoped: n/a | evidence: git grep getKeyIds over jwks tests: none in FileJwksLoaderTest, InMemoryJwksLoaderTest, JwksLoaderFactoryTest; factory test asserts LoaderStatus.OK only (115, 142); HttpJwksLoaderTest:283-285 is the only HTTP-level assertion
- OBSERVED: the file and in-memory loaders the factory returns are `JWKSKeyLoader` instances (`createFileLoader` and `createInMemoryLoader` both `return JWKSKeyLoader.builder()`), so a contract test through the factory adds entry-point coverage and not new logic — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/JwksLoaderFactory.java` lines 87-110
  - verdict: corroborated | checked_at: f85c5ab3 | by: quality-report/analyze | rescoped: n/a | evidence: JwksLoaderFactory createFileLoader and createInMemoryLoader both return JWKSKeyLoader.builder()
- HYPOTHESIS: a JWKS with no usable key never replaces an installed key set on the HTTP loader, and a test already pins it — confirm/refute at `HttpJwksLoader.java` § `updateKeys` and `HttpJwksLoaderFailureTest.java` lines 359-400 (verify-at-outline; if unpinned, point (h) adds it)
  - verdict: unverifiable | checked_at: f85c5ab3 | by: quality-report/analyze | rescoped: n/a | evidence: updateKeys behaviour for a no-usable-key JWKS not read here; verify-at-outline
- Verify-first clause: before writing a test for a point, read the implementing line and the neighbouring test it names; a point the code does not satisfy is stopped and reported, never papered over.
  - verdict: unverifiable | checked_at: f85c5ab3 | by: quality-report/analyze | rescoped: n/a | evidence: procedural verify-first clause for the consuming phase
- OBSERVED: `StepUpChallengeParser.parse` accepts only a header that starts with the `Bearer` scheme (case-insensitive) and returns `Optional.empty()` when the `error` parameter is not exactly `insufficient_user_authentication`; the client main code has no `insufficient_scope` or RFC 6750 `scope`-attribute parser — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/StepUpChallengeParser.java` lines 63-80 and `git grep insufficient_scope` over `token-sheriff-client/src/main` (empty)
  - verdict: corroborated | checked_at: f85c5ab3 | by: quality-report/analyze | rescoped: n/a | evidence: StepUpChallengeParser.java:63-80 starts-with Bearer, error equals insufficient_user_authentication else empty; git grep insufficient_scope over client main is empty
- OBSERVED: the sibling's `auth-param` handling splits on top-level commas outside quoted-strings, unescapes `\"` and `\\`, and rejects a duplicate parameter name; it does not look for control characters, bound the header length or handle a second challenge in the same value — read at `StepUpChallengeParser.java` lines 100-180
  - verdict: corroborated | checked_at: f85c5ab3 | by: quality-report/analyze | rescoped: n/a | evidence: StepUpChallengeParser.java parseParams/splitTopLevel/unquote read: top-level comma split, escape handling, duplicate param rejected; no control-character, length or second-challenge handling in the code read
- HYPOTHESIS: a value such as `Bearer error="insufficient_scope", Basic realm="x"` puts the second challenge's tokens into the Bearer parameter map in the sibling's splitter — confirm/refute at `StepUpChallengeParser.java` § `parseParams` and by a scratch test (verify-at-outline)
  - verdict: unverifiable | checked_at: f85c5ab3 | by: quality-report/analyze | rescoped: n/a | evidence: splitter reads Basic realm after a comma as a segment with key 'basic realm'; whether it leaks into the Bearer map needs a scratch test
- OBSERVED: `StepUpChallengeParserTest` has twelve tests (quoted comma, escaped quote and backslash, unterminated quote, unquoted tokens, skipped segments, blank `max_age` and `acr_values`) and none for control characters, header length or a second challenge — read at `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/StepUpChallengeParserTest.java` lines 60-227
  - verdict: corroborated | checked_at: f85c5ab3 | by: quality-report/analyze | rescoped: n/a | evidence: StepUpChallengeParserTest 227 lines, twelve test methods listed by name, none for control characters, length or a second challenge
- OBSERVED: `doc/client/requirements.adoc` defines requirements up to `CLIENT-24`, and `CLIENT-12` (RFC 9470 step-up) is realised by `doc/client/specification/step-up-and-logout.adoc` — read at `doc/client/requirements.adoc` line 228 and `step-up-and-logout.adoc` line 10
  - verdict: corroborated | checked_at: f85c5ab3 | by: quality-report/analyze | rescoped: n/a | evidence: grep: highest requirement CLIENT-24; CLIENT-12 at requirements.adoc:228; step-up-and-logout.adoc:10 realises CLIENT-12

## Expected Surface

- HYPOTHESIS: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwks/JwksLoaderKeyIdsContractTest.java` (verify-at-outline — a NEW test class, name by role)
- HYPOTHESIS: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwks/JwksLoaderKeyIdsContractSupport.java` (verify-at-outline — only if the class would pass 500 lines and is split by loader type)

- HYPOTHESIS: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/InsufficientScopeChallengeParser.java` (verify-at-outline — a NEW public class, name by role; deliverable 2)
- HYPOTHESIS: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/BearerChallengeParams.java` (verify-at-outline — a NEW package-private helper for the shared `auth-param` splitting, name by role)
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/StepUpChallengeParser.java` (deliverable 2: the splitter moves to the shared helper, behaviour-preserving)
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/StepUpChallengeParserTest.java` (deliverable 2: guard for the extraction, plus a row per stated tightening)
- HYPOTHESIS: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/InsufficientScopeChallengeParserTest.java` (verify-at-outline — a NEW test class, at or under 500 lines)
- HYPOTHESIS: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/InsufficientScopeChallengeAttackTest.java` (verify-at-outline — only if the attack matrix would push the parser test past 500 lines)
- OBSERVED: `doc/client/requirements.adoc` (deliverable 2: the new `CLIENT-25` and its traceability row)
- OBSERVED: `doc/client/README.adoc` (deliverable 2: the `CLIENT-25` mapping row)
- OBSERVED: `doc/client/specification/step-up-and-logout.adoc` (deliverable 2: the parser next to the RFC 9470 text)
- OBSERVED: `doc/client/threat-model.adoc` (deliverable 2: the header-injection and scope-smuggling row)

Read, not edited: `JwksLoader.java`, `JWKSKeyLoader.java`, `HttpJwksLoader.java`, `JwksLoaderFactory.java` and the neighbouring tests named above.

## Dependencies and Sequencing

- Depends on: nothing. Deliverable 1 is independent of PLAN-25 (running): PLAN-25 edits `JWKSKeyLoader.java`, `KeyProcessor.java`, `HttpJwksLoader.java` (log-record arguments, reason strings) and `HttpJwksLoaderFailureTest.java`; deliverable 1 only READS those and adds a new test file. Re-read the three production methods at outline, because PLAN-25's edits may move their lines; a PLAN-25 merge in between changes no contract point (it is fail-closed and log-argument only).
- Overlaps with: PLAN-25 (running) through deliverable 2's documentation, because PLAN-25 declares `doc/client/requirements.adoc`, `doc/client/README.adoc`, `doc/client/specification/step-up-and-logout.adoc` and `doc/client/threat-model.adoc` as well (its items 7(j), 8 and 10(g)). The `corpus cross-check` at emit time decides, and a multi-file overlap is the operator's call; the safe order is to emit this plan after PLAN-25 merges, so that its doc edits are on `main` first.
- Timing: deliverable 1 is wanted before the next TokenSheriff release cut (the downstream consumer reads the released tag, not `main`); deliverable 2 is new public API and not urgent (API Sheriff's PLAN-20 is queued behind other work). Being one plan, a release cut that comes first ships neither unless the lifecycle cuts deliverable 1 out at outline and records that.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/quality-report/plans/PLAN-26-jwks-key-ids-contract-pin.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
