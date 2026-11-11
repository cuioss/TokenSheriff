# PLAN-20: Resolve the committed OpenRewrite marker in TokenLifecycleManager

epic: quality-report
workstream: WS-02

## Objective

PLAN-02 (#832) committed an OpenRewrite request-to-act marker, `/*TODO: Catch specific not
RuntimeException. Suppress: // cui-rewrite:disable InvalidExceptionUsageRecipe*/`, in
`TokenLifecycleManager.holdsNewerGrant`. `AGENTS.md` ("OpenRewrite Markers") says never to commit a
marker, and Sonar reports it as the one open INFO `java:S1135` issue on #832. Answer the marker: fix the
code (preferred) or record the sanctioned suppression with its reason, then remove the marker. Closes
the unmet PLAN-02 Sonar criterion. No finding ID: this is a residual of PLAN-02.

## Deliverables

1. **Marker answered** — decide at outline between (a) narrowing the caught type and (b) the sanctioned
   suppression, and remove the `/*TODO …*/` comment either way. Recommended: (b), because the catch
   guards a read of the `TokenStore` SPI, whose `retrieve` declares no `throws` and has an open set of
   implementations, which is the one carve-out `AGENTS.md` gives for `InvalidExceptionUsageRecipe`.
   Form: `// cui-rewrite:disable InvalidExceptionUsageRecipe` on the line before the catch, with the
   existing comment already recording why. If (b) is taken, update the `AGENTS.md` sentence that names
   `TokenBuilder.java:143` as the single sanctioned instance. If (a), keep the fail-closed behaviour:
   a store that throws on the read reports `false`, so the caller clears.
2. **Behaviour pinned** — a test (new, or an existing one confirmed at outline) proves a `TokenStore`
   whose `retrieve` throws an unchecked exception still leads to a clear, not a kept grant.
3. **No marker left in the tree** — a sweep of `token-sheriff-*` for `~~(TODO` and `/*TODO` in Java
   sources returns nothing; Sonar shows 0 open issues on the PR.

## Standing Constraints

- Every claim below was re-grounded at HEAD `a56b1b59`; re-verify each against the implementing
  source at outline before scoping on it (refutation loops back to re-scope). The original report
  text is in `.plan/orchestrator/quality-report/source-report/` (read-only, stale line numbers).
- Do NOT edit `source-report/` or `findings-disposition.md` — the orchestrator records dispositions.
- Fail-closed posture is non-negotiable: the cleared-on-unreadable-store behaviour of
  `holdsNewerGrant` must not change.
- Suppression is not a valid answer to `InvalidExceptionUsageRecipe` except for the SPI carve-out in
  `AGENTS.md`; if the outline cannot show that carve-out applies, narrow the catch.
- Every behavioural fix ships with a test that fails before and passes after (JUnit 5, CUI test
  conventions); build and verify via Maven only.
- SonarCloud analyses PRs (`sonar: configured`): an open Sonar issue is fixed in a pushed commit,
  or dismissed with a stated reason recorded as `{issue-key}: {reason}` in the run's progress
  record and transmitted via `plan-marshall:workflow-integration-sonar:sonar_rest transition
  --issue-key {key} --transition {falsepositive|wontfix}`. A dismissal the run cannot transmit is
  a blocker. The merge gate reads the open-issue count at the head — `0` confirmed or every
  remaining issue dismissed; an `undecidable` read blocks.
- Scope guard: this plan is one method. Do not widen it into a refactor of `TokenLifecycleManager`.

## Claim Labels

- OBSERVED: the marker `/*TODO: Catch specific not RuntimeException. Suppress: // cui-rewrite:disable InvalidExceptionUsageRecipe*/` precedes `catch (RuntimeException unreadable)` in `holdsNewerGrant` — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/lifecycle/TokenLifecycleManager.java` § `holdsNewerGrant`
- OBSERVED: the catch guards `tokenStore.retrieve(sessionId)`, and `TokenStore.retrieve` declares no `throws` — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/lifecycle/TokenStore.java` § `retrieve`
- OBSERVED: it is the only committed OpenRewrite marker in Java sources — swept `token-sheriff-*` and `benchmarking` for `~~(TODO` and `/*TODO`
- OBSERVED: other sanctioned suppressions use `// cui-rewrite:disable InvalidExceptionUsageRecipe` on the line before the guarded construct — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/TokenValidator.java` § line 495
- HYPOTHESIS: the SPI carve-out applies to `TokenStore` (open set of implementations, no declared `throws`) — confirm/refute against `AGENTS.md` § "`InvalidExceptionUsageRecipe`: narrow the catch, do not suppress" (verify-at-outline)
- HYPOTHESIS: an existing test already covers a throwing `retrieve` in `holdsNewerGrant` — confirm/refute in `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/lifecycle/TokenLifecycleManagerFamilyEvictionTest.java` (verify-at-outline)

## Expected Surface

- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/lifecycle/TokenLifecycleManager.java`
- HYPOTHESIS: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/lifecycle/TokenLifecycleManagerFamilyEvictionTest.java` (verify-at-outline)
- HYPOTHESIS: `AGENTS.md` (only if the suppression arm is taken — verify-at-outline)

## Dependencies and Sequencing

- Depends on: none
- Overlaps with: none declared by another staged spec at staging time; run `corpus declaration-currency` before emit
- Adjacent to: PLAN-02 (shipped, #832), which introduced the marker

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/quality-report/plans/PLAN-20-rewrite-marker-cleanup.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
