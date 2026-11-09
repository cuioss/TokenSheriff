# PLAN-26: Pin the `JwksLoader.getKeyIds()` contract with a contract test

epic: quality-report
workstream: WS-01

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Staged 2026-11-09 at the operator's instruction ("spec a test"), from the API Sheriff downstream request
> (`api-sheriff-kidicap-gateway-requirements-001`, observed at `f85c5ab3`; the release information in its part A was
> declined by the operator). The orchestrator EMITS the command below; it never launches the plan inline.

## Objective

API Sheriff's next plan replaces its approximate JWKS refresh rule with one that compares the key ids of the fresh
and the current active key set, and so depends on `JwksLoader.getKeyIds()` as a documented contract. The contract is
written in the interface Javadoc and is already pinned in several places, but only piecemeal and mostly below the
public types: the blank-id and duplicate-id cases are asserted on `JWKSKeyLoader` only, and nothing asserts the contract
once, in one place, through the three public entry points (`JwksLoaderFactory.createHttpLoader`, `createFileLoader`,
`createInMemoryLoader`). This plan adds one contract test class that states each point of the contract and runs it against
every loader type the factory returns, so a downstream consumer reads one document and a regression in any
implementation fails one named test. Test-only: no production change, no new behaviour, nothing widens.

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

## Standing Constraints

- Test-only. No production line changes, no new `LogRecord`, no change to `doc/LogMessages.adoc`, no change to the
  `JwksLoader` Javadoc unless a point is found false (then stop and report, per deliverable 1).
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

## Expected Surface

- HYPOTHESIS: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwks/JwksLoaderKeyIdsContractTest.java` (verify-at-outline — a NEW test class, name by role)
- HYPOTHESIS: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/jwks/JwksLoaderKeyIdsContractSupport.java` (verify-at-outline — only if the class would pass 500 lines and is split by loader type)

Read, not edited: `JwksLoader.java`, `JWKSKeyLoader.java`, `HttpJwksLoader.java`, `JwksLoaderFactory.java` and the neighbouring tests named above.

## Dependencies and Sequencing

- Depends on: nothing. Independent of PLAN-25 (running): PLAN-25 edits `JWKSKeyLoader.java`, `KeyProcessor.java`, `HttpJwksLoader.java` (log-record arguments, reason strings) and `HttpJwksLoaderFailureTest.java`; this plan only READS those and adds a new test file, so no file is shared. Re-read the three production methods at outline, because PLAN-25's edits may move their lines; a PLAN-25 merge in between changes no contract point (it is fail-closed and log-argument only).
- Overlaps with: none declared. The `corpus cross-check` at emit time decides.
- Wanted before the next TokenSheriff release cut (the downstream consumer reads the released tag, not `main`), so it is small on purpose.

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
