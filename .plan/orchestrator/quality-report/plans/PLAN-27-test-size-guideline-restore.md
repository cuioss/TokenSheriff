# PLAN-27: Correct two PLAN-25 outcomes — explicit JSON null read as absent again, and the 500-line test-size guideline

epic: quality-report
workstream: WS-05

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Staged 2026-11-09 at the operator's decision on the PLAN-25 landing (`landings/PLAN-25.md`): of the four decisions PLAN-25 shipped
> differently from the mailbox, the operator kept the JWE limit (raised to 32 KiB), the numeric-`sub` refusal and the default-on client
> egress policy, and reverted two things: the explicit-null refusal ("I said it only for the null values", so ONLY null goes back to
> absent, and the strict string type check stays) and the test-size rule. The file name and the queue slug (`test-size-guideline-restore`)
> are those of the first version of this spec and are historical: a queue row's slug cannot be edited, and the plan is identified by
> `PLAN-27` and this title. The orchestrator EMITS the command below; it never launches the plan inline.

## Objective

PLAN-25 shipped two behaviours the operator did not choose. First, an explicit JSON `null` for the identifier claims `iss`, `sub`, `jti`,
`client_id` and `azp` is refused as a counted `MISSING_CLAIM` rejection; the operator's decision is that a null is treated as an absent
claim, as the code did before, pinned by a test and described correctly in the docs. A present value that is not a JSON string (a number,
boolean, array or object, and a numeric `sub`) STAYS refused: the operator kept that. Second, `AGENTS.md` § Test Class Size says "a test
class should stay within 400 lines" and "more than 1000 lines must be split"; the operator's decision is a guideline, more than 500 lines
should be avoided, with no obligation to split an existing class. This plan puts both back, and nothing else: it does not touch the JWE
limit, the numeric-`sub` refusal, the userinfo `sub` converter or the egress policy.

## Deliverables

1. **An explicit JSON null in an identifier claim is read as an absent claim again** — the claims are `iss`, `sub`, `jti`, `client_id`
   and `azp` of an access or ID token. The consumer-visible field and edge set, one verification criterion each:
   (a) for each of the five claims, a token whose body carries the claim with the JSON value `null` produces exactly the result a token
   with that member omitted produces (accepted or refused by the same later check, same event type), asserted by one parameterised
   equivalence test in `TokenBuilder` terms (name by role, e.g. `TokenBuilderExplicitNullTest` rewritten, or a row set in
   `ClaimNameTest` as the 2026-11-07 mailbox said); this is the pin the operator asked for, and it replaces the refusal tests;
   (b) a body made only of explicit nulls is treated like an empty body, as before PLAN-25 (the PLAN-25 comment "a body of only explicit
   nulls is empty, so the refusal runs before the empty-body return" goes with the refusal);
   (c) a present non-string value is STILL refused, unchanged: a number, boolean, array or object for `iss`, `sub`, `jti`, `client_id`
   and `azp` where a strict mapper applies today, and a numeric `sub` even under an issuer-configured custom mapper (the
   `STRICT_STRING_GUARD` call for `sub` in `TokenBuilder` stays); the existing tests for these stay green and unedited;
   (d) an explicit null for a NON-identifier claim is read as absent, as before PLAN-25 (the PLAN-25 loop that routed such a null through
   the claim's own mapper goes; confirm at outline what the code did before, from `git show 2eceb09e:` the same file);
   (e) `MapRepresentation` returns to `MapRepresentation(Map<String, Object> data)`: the `explicitNullKeys` component, the
   `isExplicitNull(String)` accessor and the `ofParsed` split go, and the Javadoc is restored; the commit that added them is in no
   release (last tag `0.9.8`, `git tag --contains 47045031` is empty), so no released API is removed; the compact-constructor and the
   nested-map behaviour match `2eceb09e`;
   (f) `ClaimName.isExplicitNullRefused()` and its Javadoc go; the `ClaimName` Javadoc for `SUBJECT`, `CLIENT_ID` and `AUTHORIZED_PARTY`
   keeps its "must be a JSON string" text and loses its "an explicit JSON null is refused" sentences; the `StrictStringMapper` null
   branch and the two Javadoc sentences go, leaving a present non-string refused and an absent claim `null`;
   (g) the userinfo path is NOT changed: `UserInfoResponse.StrictSubjectConverter` already reads an explicit JSON null as an absent `sub`
   and refuses any other non-string (read at `token-sheriff-client/.../token/UserInfoResponse.java`); its tests stay;
   (h) documentation in the same change: `doc/validation/security-reference.adoc` (the line that says an explicit null "is refused as a
   counted MISSING_CLAIM rejection ... not read as an absent claim", and the `azp` and `client_id` bullets that say "an explicit null is
   refused") now say a null is read as an absent claim and a present non-string is refused; `ClaimName.java`'s Javadoc as in (f). A grep
   for `explicit.{0,12}null` over `doc/` and `*.java` at implementation time decides whether other statements exist; each is corrected.
   **Fail-closed statement (required in the PR body):** a token is never accepted because of this plan that would be refused if the member
   were omitted; an absent required claim is still refused by the existing required-claim check, and the PR body names the check per claim.
   **Non-vacuity (required):** in a scratch run, reintroduce the refusal (the `isExplicitNull` branch in `StrictStringMapper`), record that
   the new pin test fails, then restore it; the mutation is never committed. The PR body lists the line and the failing test.
2. **`AGENTS.md` § Test Class Size states the 500-line guideline** — replace the section at `AGENTS.md` lines 124-131 so that it says,
   one verification criterion each:
   (a) the figure is 500 lines and it is a guideline ("should be avoided"), not a hard rule and not a build or review gate;
   (b) no 1000-line "must be split" rule remains and no "exactly 1000 is exempt" clause remains;
   (c) an existing class over 500 lines is NOT required to be split because of this guideline; a class that a change creates, or that a
   change grows past 500 lines, should stay at or under 500, and when it cannot, the PR body gives the reason;
   (d) the split mechanics that are still useful stay as advice, not as a rule: a split by topic into sibling classes
   `{Original}{Topic}Test` plus at most one package-private `{Original}Support` fixture, a pure move with no assertion, input or expected
   value changed and an identical executed test count;
   (e) the section records the state it was written against, measured by a line count at implementation time (the figures recorded at
   `d1af3ebb` were 52 classes over 400, 18 over 600 and four at or over 1000; re-measure, do not copy).
3. **Stale "400-line budget" wording removed** — three client test Javadocs refer to "the module's 400-line budget":
   `RefreshInFlightFailureTest.java:72`, `RefreshIdTokenValidationQuarantineTest.java:65` and `RefreshTestSupport.java:49`
   (all in `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/token/`). Reword each to say the class was split to keep
   it readable and near the 500-line guideline in `AGENTS.md`; a comment-only edit. A sweep of the whole tree
   (`git grep -nE '400[- ]line'` outside `.plan/`) at implementation time decides whether more occurrences exist; each is reworded the same way.

**Not in this plan:** the JWE size limit (stays raised to at most 32 KiB), the numeric-`sub` and userinfo-`sub` refusals, the default-on
client egress policy, splitting any test class (including `NonValidatingJwtParserTest`, 1023 lines after PLAN-25), reverting the four
splits PLAN-25 already made, and the DPoP findings (separate, only on the operator's word).

## Standing Constraints

- Deliverable 1 changes behaviour in one direction only, and only for an explicit null: the result for a null equals the result for an
  omitted member. No present value that is refused today is accepted afterwards. Deliverables 2 and 3 are documentation and comments only.
- Test-size guideline (operator, a guideline): more than 500 lines should be avoided; test classes this plan creates or grows stay at or
  under 500 lines, and a class whose edit would push it past 500 is split in the same change as a pure move with no assertion changed.
- JUnit 5 and the CUI test conventions already used in the neighbouring tests (`cui-test-generator`, `@DisplayName` per test); build and
  verify via Maven only. A removed test is removed because the behaviour it pinned is gone, and the PR body lists each removed or rewritten
  PLAN-25 test (`TokenBuilderExplicitNullTest`, the explicit-null rows of `MapRepresentationTest`, `StrictStringMapperTest` and
  `ClaimNameTest`) with the reason.
- Fail-closed posture: nothing here widens what is accepted beyond "a null equals an omitted member"; state it in the PR body.
- Tool findings (Sonar, OpenRewrite markers) are fixed immediately; suppression only in the rule's sanctioned syntax with a stated
  reason. SonarCloud analyses PRs (`sonar: configured`): an open Sonar issue is fixed in a pushed commit, or dismissed with a stated reason
  recorded as `{issue-key}: {reason}` and transmitted through
  `plan-marshall:workflow-integration-sonar:sonar_rest transition --issue-key {key} --transition {falsepositive|wontfix}`. The merge gate
  reads the open-issue count at the head: `0` confirmed or every remaining issue dismissed; an `undecidable` read blocks. Read the count
  with a branch- or PR-scoped `resolved=false` filter, never an unfiltered issue search.
- Every claim below was grounded at `aeb3fa49` (main after PLAN-25). Re-verify each against HEAD at outline before scoping on it.
- The plan creates and edits no file under `.plan/orchestrator/` other than its own inbox message (Write-Boundary).

## Claim Labels

- OBSERVED: `StrictStringMapper.map` throws a `MISSING_CLAIM` `TokenValidationException` for an explicit JSON null (`isExplicitNull`) and for a
  present non-string, and returns `null` for an absent claim — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/domain/claim/mapper/StrictStringMapper.java` § `map`
- OBSERVED: `TokenBuilder.refuseExplicitNulls` loops over `mapRepresentation.explicitNullKeys()`, routes an identifier-claim null through the strict
  guard and any other claim's null through that claim's own mapper, and runs before the empty-body return; a separate block applies the strict guard to
  `sub` before a custom mapper — read at `.../validation/pipeline/TokenBuilder.java` § `refuseExplicitNulls` and the `SUBJECT` branch
- OBSERVED: `MapRepresentation` is `record MapRepresentation(Map<String, Object> data, Set<String> explicitNullKeys)` with `isExplicitNull`, `ofParsed` and a
  convenience constructor `MapRepresentation(Map)` — read at `.../validation/json/MapRepresentation.java`; before `47045031` it was a one-component
  record that filtered null values out (`git show 2eceb09e:` the file)
- OBSERVED: `ClaimName.isExplicitNullRefused()` returns true for `ISSUER`, `SUBJECT`, `TOKEN_ID`, `CLIENT_ID` and `AUTHORIZED_PARTY` — read at
  `.../validation/domain/claim/ClaimName.java` § `isExplicitNullRefused`
- OBSERVED: the userinfo `sub` converter already reads an explicit null as an absent `sub` and refuses every other non-string — read at
  `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/token/UserInfoResponse.java` § `StrictSubjectConverter`
- OBSERVED: the commit that added the explicit-null API is not in any release: the last tag is `0.9.8` and `git tag --contains 47045031` is empty
- OBSERVED: `doc/validation/security-reference.adoc` says an explicit null for the five claims "is refused as a counted `MISSING_CLAIM` rejection", and
  its `azp` and `client_id` bullets repeat it — read by `git diff 47045031 aeb3fa49 -- doc/validation/security-reference.adoc`
- OBSERVED: the references to the explicit-null API outside `.plan/` are `MapRepresentation`, `ClaimName`, `StrictStringMapper`, `TokenBuilder` and the
  tests `MapRepresentationTest` and `ClaimNameTest` (`git grep -l 'isExplicitNull\|explicitNullKeys\|isExplicitNullRefused'`), plus the new
  `TokenBuilderExplicitNullTest` and `StrictStringMapperTest` rows
- HYPOTHESIS: before PLAN-25 an explicit null for a non-identifier claim was read as absent without calling the claim's mapper — confirm/refute at
  `git show 2eceb09e:.../TokenBuilder.java` (verify-at-outline; criterion 1(d) follows what it showed)
- HYPOTHESIS: every one of the five claims has a refusal path when it is required (sub by `TokenClaimValidator`, `iss` by the issuer lookup, `jti`/`azp`/`client_id` by
  their own validators or optionality) so an absent member is refused where a null is refused today — confirm/refute per claim at the validator named
  (verify-at-outline; any claim for which an absent member is accepted but a null is refused today is a behaviour widening to be listed in the PR body)
- OBSERVED: `AGENTS.md` § Test Class Size says "a test class should stay within 400 lines" (line 125), "more than 1000 lines must be split" (line 126) and
  "exactly 1000 lines is within the rule" (line 131) — read at `AGENTS.md` lines 124-131
- OBSERVED: three client test Javadocs cite "the module's 400-line budget" — read at `RefreshInFlightFailureTest.java:72`,
  `RefreshIdTokenValidationQuarantineTest.java:65` and `RefreshTestSupport.java:49` (`git grep -nE '400[- ]line'` outside `.plan/`)
- HYPOTHESIS: no other versioned document states the 400 or 1000 figure — confirm/refute with `git grep -nE '400[- ]line|1000 lines'` (verify-at-outline)
- Verify-first clause: before removing a line of the PLAN-25 explicit-null code, read what `2eceb09e` did in the same place and restore that; a pre-PLAN-25
  behaviour that the code does not show is stopped and reported, never guessed.

## Expected Surface

- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/domain/claim/mapper/StrictStringMapper.java` (deliverable 1)
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/domain/claim/ClaimName.java` (deliverable 1)
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/json/MapRepresentation.java` (deliverable 1)
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/TokenBuilder.java` (deliverable 1)
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/pipeline/TokenBuilderExplicitNullTest.java` (deliverable 1: rewritten as the pin)
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/json/MapRepresentationTest.java` (deliverable 1)
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/domain/claim/ClaimNameTest.java` (deliverable 1)
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/domain/claim/mapper/StrictStringMapperTest.java` (deliverable 1)
- OBSERVED: `doc/validation/security-reference.adoc` (deliverable 1)
- OBSERVED: `AGENTS.md` (deliverable 2: § Test Class Size, lines 124-131)
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/token/RefreshInFlightFailureTest.java` (deliverable 3: Javadoc only)
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/token/RefreshIdTokenValidationQuarantineTest.java` (deliverable 3: Javadoc only)
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/token/RefreshTestSupport.java` (deliverable 3: Javadoc only)

Read, not edited: `token-sheriff-client/.../token/UserInfoResponse.java`, `UserInfoClientTest.java`, `TokenClaimValidator.java`, the issuer-lookup and the other validators named in the hypothesis.

## Dependencies and Sequencing

- Depends on: nothing (PLAN-25 is merged).
- Overlaps with: PLAN-26's surface is `token-sheriff-validation/src/test/.../jwks/`, `token-sheriff-client/.../flow/` and `doc/client/`; none of this plan's files is in it, so the two may run in parallel.
- Adjacent to: the JWE size path (`NonValidatingJwtParser`, `JweDecryptionConfig`) and the numeric-`sub` refusal, which stay untouched; `TokenBuilder` is edited only
  where the explicit-null handling sits and the `sub` strict-guard block must survive the revert.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/quality-report/plans/PLAN-27-test-size-guideline-restore.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
