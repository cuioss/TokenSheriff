# Upstream report for plan-marshall: friction seen in the quality-report epic

epic: quality-report
written: 2026-10-31
plugin version in use: 0.1.2027 (landing messages)
status: FILED 2026-11-05 at the operator's instruction as `quality-report-001.md` in the plan-marshall repository's
`live-blockers` inbox (`.plan/local/worktrees/_orchestrator/.plan/orchestrator/live-blockers/inbox/`), kind `finding`. Content as of the
PLAN-23 addendum. Later addenda are not sent; the operator decides whether to file them.

Twenty-four `candidate-lesson` messages from PLAN-01 to PLAN-07 name `plan-marshall:*` components, which
this repository cannot fix. They are grouped into six themes. Each theme was also filed as one lesson in
this repo's lesson store with the foreign-store override (`2026-10-31-13-001` to `-006`) so agents running
plans here see the rule. Every statement below comes from the senders' messages and is NOT independently
verified by the orchestrator.

## 1. CodeRabbit's one-review-per-hour quota (5 messages)

Senders: PLAN-01-003, PLAN-02-003, PLAN-03-005, PLAN-04-004, PLAN-06-001. Component: `automatic-review`,
`workflow-integration-github`, `manage-locks`.

- Every plan hit it. A fix push or a late docs commit moves HEAD and the required bot is
  `refused_awaitable (quota)` for the new head; the pre-merge barrier reports `participation_complete=false`
  and the plan merges only under `barrier-ask-override` (PLAN-01, 03, 04, 06, 07).
- Concurrent plans share one account quota: PLAN-06 waited about two hours behind PLAN-07's rate window
  (refusal recovery deferred, then armed, then completed after about two hours).
- After a content-identical rebase the incremental reviewer answered "No files to review", leaving the PR
  without a current review; only the registry `--escalated` full-review command produced one (PLAN-02).
- Suggested upstream work: batch review-driven fixes into one push; treat a docs-only delta after full bot
  coverage as lower risk at the barrier; account for the shared window when pairing plans.

## 2. Merge gate bound to the exact HEAD (4 messages)

Senders: PLAN-01-001, PLAN-01-002, PLAN-02-002, PLAN-06-002. Component: `phase-6-finalize` (pre-merge
barrier), `manage-status` (merge-authorization grant).

- PLAN-01: a formatting commit and a docs-only ADR commit after the gates ran gave `merge_candidate_not_gated`;
  six gates re-ran.
- PLAN-01: the grant verb refused the paraphrased answer "Accept gap and merge"; it accepts only the
  barrier's own label "Merge anyway (record reason)".
- PLAN-02: `finalize-step-sync-baseline` stamped an old `head_at_completion` and was skipped on re-entry as not
  head-dependent, but the gate derives head-dependence from that stamp; main advanced, a rebase was the only
  fix, and every verdict was voided.
- PLAN-06: a test-only commit after the gated head; the step records accepted the operator's re-run waivers
  but the merge gate refused them (`merge_candidate_not_gated`, security audit and functional review), and
  both re-ran about three hours later.
- Suggested upstream work: one source for the skip rule and the gate's head-dependence; a cheap
  re-attestation path for docs-only deltas; a prompt envelope that carries the barrier's own labels; make the
  step records and the gate agree on whether a waiver counts.

## 3. Installed scripts lag the skill docs (7 messages)

Senders: PLAN-01-004, -005, -006, PLAN-04-008, PLAN-06-003, PLAN-07-003, PLAN-07-005. Component: various
(`tools-integration-ci`, `manage-findings`, `manage-architecture`, `tools-script-executor`, `phase-6-finalize`,
`automatic-review`, `build-maven`).

- Documented but not registered: `pre_merge_gate record-merge` and `consequence-text`;
  `review_completeness roster` (rejected 6 times in one plan; registered: check, deficit, size-caps,
  trigger-bot).
- Invented or misplaced: `ci pr list --state merged` (valid: open, closed, all), `manage-findings query`,
  `manage-findings pr-comment`, `manage-config effort resolve-target --step`, `architecture --plan-id P search
  --content`, missing required flags on `ci_complete_precondition resolve` and `ci_verify run`.
- A recovery action returned `close_and_reopen` where the doc says `generate_trigger`; the executor drift
  probe returned `drift=unknown`, so drift was never detected.
- For the `architecture` rejections the failure marker keeps only the argv, not the argparse error, so the
  cause cannot be read from the record (PLAN-01-006).
- PLAN-04-008: a cluster of failed full verifies and a bare `test-compile` was transmitted unclassified; the
  underlying work-log lines were not re-analysed.
- Suggested upstream work: keep installed scripts and docs in step (a version-parity check); make every
  failure marker record the raw argv and the argparse error text.

## 4. Bookkeeping slips (3 messages)

Senders: PLAN-03-004, PLAN-07-002, PLAN-07-004. Component: `manage-status`, `manage-change-ledger`,
`phase-3-outline`, `phase-4-plan`, `manage-metrics`.

- PLAN-03: an inline review-fix commit had no `kind=change` ledger row; the 5-execute to 6-finalize
  transition was refused (`execute_commits_unrecorded`).
- PLAN-07: six per-deliverable commits carried `Co-Authored-By: Claude Opus 5.5` where the operator required
  `Claude Sonnet 5.5`; rewriting them left ledger rows on the old SHAs and the same transition was refused.
- PLAN-07: `phase-3-outline` and `phase-4-plan` transitioned the phase themselves, so the metrics boundary
  was stamped retroactively with the repair time (06:01:59Z and 07:58:31Z), skewing per-phase figures.

## 5. Outline and plan quality (4 messages)

Senders: PLAN-02-005, PLAN-03-003, PLAN-04-003, PLAN-04-007. Component: `phase-3-outline`, `execute-task`.

- A criterion about the PR body can never be evidenced by the pre-PR functional review (PLAN-04).
- The outline missed the docs that list a closed set the deliverable extended (PLAN-04).
- A fix-task agent edited files outside its declared set and left an outline "no LogMessages change" note
  stale (PLAN-02).
- A "fails before the fix" criterion was evidenced only at review by overlaying tests on the base commit; for
  a signature change the proof is a compile failure (PLAN-03).

## 6. Stale PR body after loop-back (1 message)

Sender: PLAN-01-008. Component: `phase-6-finalize` (create-pr, loop-back re-entry).

- The PR body went stale after fix tasks TASK-004 to TASK-009; the functional review flagged two false
  self-reports, and the squash merge used the stale body as the commit message.
- Suggested upstream work: regenerate the body on loop-back re-entry.

## Also noted in landing messages (not lessons)

- `branch-cleanup` was recorded through `mark-step-done` in PLAN-06 and PLAN-07 (PLAN-05 reports the same missing verb) because `pre_merge_gate
  record-merge` is not registered.
- `effort` was not stamped in the PLAN-01, 02, 04 and 05 landings (`unknown`); the runtime stamp could not be
  refreshed (`model_unknown`) in several.
- PLAN-07's `branch-cleanup` was recorded with the clean merge wording although a `barrier-ask-override` was
  granted, so that step alone hides the grant.

## Addendum 2026-11-01: PLAN-12 and PLAN-20 (9 more messages)

Recurrences of the themes above (not filed as new lessons):

- Theme 3, scripts lag the docs: PLAN-12-001 and PLAN-20-003. The generated executor was 0.1.2027 while the
  workflow docs read were 0.1.2040 (PLAN-12) and 0.1.2046 (PLAN-20). `pre_merge_gate consequence-text` and
  `record-merge`, `review_completeness roster`, `manage-solution-outline get-deliverable --number`,
  `manage-findings qgate query` and the `generate_trigger` selector result are missing or differ. Both senders
  name regenerating the executor (marshall-steward executor staleness) as the likely fix. A third and fourth
  plan hit the same drift, so a doc-version versus executor-version check before a plan run would prevent it.
- Theme 1, CodeRabbit quota: PLAN-12-005. The required bot was quota-limited for the last two fix commits and
  the automatic-review step could not reach completion; the merge went through under "Accept the coverage gap".
  PLAN-20 also had two quota refusals, switched `review_rate_window_await` on after the first, and waited about
  25 hours of wall time in all.
- Theme 2, head binding: PLAN-20-004. A docs-only follow-up commit after the pre-push gate re-ran every
  head-dependent finalize step, about an hour including the quota wait. PLAN-12-004 is the sibling case: the
  pre-commit profile reformats tracked files after the last commit, so a green build is bound to a HEAD that no
  longer matches the tree to be pushed. Suggested: run the reformatting profile before the final commit, make
  verify fail on a dirty tree after the build, or bind verification records to the tree hash.
- Theme 5, outline quality: PLAN-20-001. The solution outline gave two different fallbacks for the same
  re-injection case; the phase-3 quality gate caught the contradiction. State each case's fallback in one place.

New items:

- PLAN-12-002 (with its correction PLAN-12-003): a code-review finding with `fix_kind correction` has no
  applier when the review step declares `mutates_source: false`, so it loops back for an in-place correction
  that nothing can perform, and was closed as accepted. The sender later corrected itself: on main the
  `CoreJfrBenchmark` defect IS fixed (the token is selected once and that same token is validated), so the
  statement in 002 that it was not fixed is wrong. What still holds is the routing gap. The related functional
  review findings (no failing pre-fix run for `BenchmarkStateScopeTest`) were closed the same way.
- PLAN-20-002: an advisory code-review finding about documentation claims that outrun their sources was not
  acted on before the PR went up, and CodeRabbit then raised the same defect as an actionable comment, costing
  an extra review round. The operator's fix-immediately policy covers it; whether the review step should
  surface advisories of this class before push is a plan-marshall question.

## Addendum 2026-11-02: PLAN-21 (6 plan-marshall messages)

PLAN-21-004 (a lock/publish race plus under-pinned tests) is project-owned and was filed as the local lesson
`2026-11-02-10-001`; it is not part of this report. The other six name `plan-marshall:*` components.

Recurrences of the themes above (not filed as new lessons):

- Theme 2, head binding: PLAN-21-002 and PLAN-21-003. The first sync-baseline record forwarded
  `--head-at-completion`, which that step's workflow doc does not record. The pre-merge barrier later compared
  the extra binding with the merge candidate and refused with `merge_candidate_not_gated`, so the record had to
  be redone without the flag. Suggested: the recording verb rejects a head binding the step does not own. And
  PLAN-21-003 is the third plan (after PLAN-20-004 and PLAN-12-004) where a fix commit made by triage after the
  pre-push gate could not be pushed until the gate and the whole build re-ran: either route the re-gate
  automatically in the triage fix loop or batch review-bot follow-ups and gate findings before the gate runs.
- Theme 3, scripts lag the docs: no new case. PLAN-21 ran on `executor_version` 0.1.2046, equal to the plugin
  version, so the drift reported in the first addendum is gone once marshall-steward regenerated the executor.
- Theme 5, outline and plan quality: PLAN-21-005 and PLAN-21-006. The phase-4 description-drift Q-Gate flagged
  ADR numbers (`17`, `18`, `16`) that the phase-4 ADR scan itself had injected into task descriptions as
  lifecycle-contract pointers; the outline never mentions them. Both findings were accepted by hand. Suggested:
  exempt references that originate from the ADR scan, or have the scan record its pointers so the validator can
  read them.

New items:

- PLAN-21-001: the pre-push gate's `test-compile` arm cannot succeed for a module that consumes the
  `token-sheriff-validation` generators test-jar. The test-jar is attached at `package`, so a reactor
  `test-compile` that stops earlier cannot resolve it, yet `architecture resolve test-compile` still emits that
  command for the consumer modules. The failed attempt filed 120 `build-error` findings with one root cause,
  which had to be resolved by hand, and the landing recorded `test-compile` and `module-tests` as UN-GATED. The
  same degradation was recorded for PLAN-05 and PLAN-12. Suggested: detect test-jar producer/consumer pairs and
  either resolve `test-compile` to a goal that reaches `package` for the producer or mark it unsupported for
  such modules; and collapse build failures that share one unresolvable artifact into one finding.
- PLAN-21-007: `module_mapping_validator` flagged a `module_testing` task whose files were mechanical
  caller updates after a signature change (client lifecycle code, Quarkus integration tests), because no
  implementation task lived in the test module and `doc/developer/test-impl-mapping.adoc` does not exist.
  Accepted. Suggested: route compile-compat caller updates into the implementation task, not into
  `module_testing`.

## Addendum 2026-11-02 (second): PLAN-08 (10 plan-marshall messages)

PLAN-08-002, -004, -005, -006 and -007 are project-owned (config docs and Javadoc promising more than the code,
`@DefaultBean` wording, a security fixture without a negative control). They were filed as the local lessons
`2026-11-02-14-001` and `-002` and are not part of this report.

Recurrences of the themes above (not filed as new lessons):

- Theme 5, outline quality: PLAN-08-001. A success criterion said "any `.properties` file found is fixed here",
  which implies writes to files the architecture inventory does not index, so the outline could not list them
  under Affected files; phase 4 listed candidate directories as conditional changes instead. Suggested:
  enumerate the candidate files by a direct read, or narrow the criterion to "verified none exist".
- Theme 5 and the finalize loop: PLAN-08-003. A code-review loop-back fix touched rows of a file whose
  per-deliverable scope had been narrowed by an overlap carve-out with another plan, so the functional review
  flagged it as out of scope and the operator had to accept a scope exception. Suggested: attribute the fix to the
  deliverable that owns the feature, or let the carve-out name the rows that stay open to this plan.
- Theme 3, verbs paraphrased: PLAN-08-008 and PLAN-08-011. `manage-solution-outline get-deliverable --number`
  (the flag is `--deliverable-number`; also seen in PLAN-12-001 and PLAN-20-003) and
  `review_completeness check --pr-number` (the verb is plan-scoped and takes no PR number). Check whether a doc
  quotes the short form; if not, this is one more recurrence of the argparse-rejection lesson.

New items:

- PLAN-08-009: `manage-tasks list --status pending,in_progress` is rejected because `--status` takes one value,
  yet "open tasks" (pending plus in_progress) is the natural query after verification feedback adds fix tasks.
  Suggested: accept a repeatable or comma-separated `--status`.
- PLAN-08-010: the 5 → 6 phase transition was refused twice because commits made outside the per-deliverable
  commit (a finalize code-review loop-back fix, triage inline fixes) carry no `kind=change` ledger row. The guard
  is right; the inline-commit paths should append their own row. The log records only the first missing SHA.
- Landing residue of PLAN-08: the pre-merge gate refused on 120 stale pending `build-error` findings that the
  build tooling's dedupe had re-opened when the (pre-existing) red `test-compile` re-observed them, after a later
  green build had resolved them. Same root cause as PLAN-21-001. Dedupe should not re-open a finding that a later
  green build resolved.

## Addendum 2026-11-04: PLAN-09 (4 plan-marshall messages)

PLAN-09-002 (a COVERED register row needs a wired test on every path) and PLAN-09-005 (the DPoP proof path must
enforce the same key checks as the token path) are project-owned and were filed as the local lessons
`2026-11-04-13-001` and `-002`; they are not part of this report. The other four name marketplace components.

Recurrences of the themes above (not filed as new lessons):

- Theme 3, verbs paraphrased: PLAN-09-006. Two more argparse rejections, and this time both were issued by the
  orchestrating main-context session, not by a dispatched leaf: `manage-execution-manifest record-step` with a
  flag it does not declare (the real flags are `--plan-id --step-id --phase --outcome`, with `--phase` one of
  `5-execute` / `6-finalize`), and `manage-findings` with `query` and `qgate query` (the real read verbs are
  `list` / `get` and `qgate list --phase P`; `qgate query` was already named in the first addendum). The rule in
  `agent-behavior-rules.md` § Never invent script subcommands already covers dispatched leaves. This run shows it
  also has to bind the main-context session, which drives `manage-*` scripts ad hoc between dispatches with no
  loaded workflow quoting the canonical invocation. Suggested: run `{script} {verb} --help` before an ad-hoc call
  no loaded workflow spells out.

New items:

- PLAN-09-001: a deliverable that changes a documented semantic (here the expiry boundary `exp + skew < now`)
  left three prose restatements of the old rule (a Javadoc in `IssuerConfig`, a config-key doc in
  `JwtPropertyKeys`, and a specification page). Only the finalize code review found them. Fixing them added files
  outside the declared footprint, which with two review-driven test files and one threat-model row pushed the plan
  to six out-of-scope files (threshold five) and a hand-accepted scope-creep warning at the pre-merge gate.
  Suggested: at outline, search the repository for the literal old expression and its key names, and list every
  restating file in the deliverable's affected files; or have the scope-creep check treat a documentation-only
  correction of the changed semantic as in scope.
- PLAN-09-003 (`pm-dev-java:java-security`): inserting a new check ahead of existing steps dropped a precondition
  the downstream step enforced implicitly (a null signature used to be rejected by the format converter; after
  the change the new check threw `NullPointerException`, breaking the declared `SignatureException` contract), and
  placing the check after `initVerify` made its own guard branches unreachable, so the tests passed for the wrong
  reason. Suggested for the skill: list the preconditions a downstream step used to enforce, keep the exception
  type and message, and place a new guard before any provider call that could reject the same input first.
- PLAN-09-004 (`pm-dev-java:junit-core` / `persona-module-tester`): CodeRabbit found three test gaps where a guard
  existed and no test failed when only that guard was removed: an inner-JWS empty signature masked by an earlier
  part-count check, rejection tests run in one decode mode only, and a range check with separate `r == 0` and
  `s == 0` conditions covered only by an all-zero input. The same class as the local lesson `2026-11-02-10-001`
  (tests that pin less than the spec names). Suggested for the skill: one test input per reject condition, built
  to pass every earlier check, run across every mode with the existing cross-product helpers.

Landing residue of PLAN-09: the pre-push gate arms had no standalone `module-tests` and no valid whole-tree
`test-compile` (same root cause as PLAN-21-001; the fifth plan with this degradation after PLAN-05, PLAN-08,
PLAN-12 and PLAN-21); `effort` was not stamped again (`unknown`); two metrics rows carried placeholder figures written before
the agent usage arrived and were followed by corrected rows; the post-merge `Run JMH Benchmarks` workflow was
still running when the post-merge wait hit its deadline; the merge-queue squash message is platform-composed.

## Addendum 2026-11-04: PLAN-11 (2 plan-marshall messages)

Both messages recur items above; nothing here was sent.

- PLAN-11-001 (outline / Q-Gate): a deliverable's change-per-file entry said "if a new log identifier turns out to
  be required, add it to `JWTValidationLogMessages` and `doc/LogMessages.adoc`", and neither file was in "Files
  expected to mutate". The Q-Gate `scope_criterion_validator` flagged it as `under_coverage` (finding 835478, warning)
  and the phase-4 `declared_set_closure` referrer check would have caught the write. Recurrence of the "conditional
  changes instead of declared files" items earlier in this report. Suggested: at outline, resolve a conditional
  "if X is needed also change F" clause (commit to one branch) or require F in the declared set; a new `LogRecord`
  always implies the doc file, so the check could add it automatically.
- PLAN-11-002 (pre-push gate / `build-maven` canonical resolution): same root cause as PLAN-21-001. New here: the
  failed stand-alone `test-compile` was parsed into 120 `build-error` findings that blocked the first merge
  dispatch until each was resolved as `rejected` by hand, which PLAN-04-008 saw in smaller form. Suggested, in
  addition to the test-jar producer/consumer detection already proposed: file one structural build failure as a
  single finding (or a cluster) rather than one per compile error, so it cannot hold a merge behind a rejection batch.

Landing residue of PLAN-11: the sixth plan with the degraded pre-push gate (after PLAN-05, PLAN-08, PLAN-09, PLAN-12
and PLAN-21); the project default `final_merge_without_asking: true` (`.plan/marshal.json`) would have merged without
asking, which conflicts with the repository rule in `CLAUDE.md` that auto-merge is never enabled unless told, so the
operator authorised the merge by hand for this PR; `effort` was stamped this time.

## Addendum 2026-11-05: PLAN-10 (7 plan-marshall messages)

Nothing here was sent. Lessons 002 (negative `max_age` plus skew) and 004 (DSL-JSON unchecked exception) are
project-owned and are handled in the epic ledger, not here. Lesson 005's project half (write the suppression marker
up front) is already the operator's OpenRewrite rule in the epic.

- PLAN-10-001 (review / doc restatement): a best-practice paragraph restated a rule without the scope qualifier the
  owning spec section carries ("for a JWT access token"); two functional and code review rounds over the same files
  missed it and the external bot caught it. Suggested: when a deliverable restates a normative rule in a summary
  document, the review compares the restatement with the owning section, not only with the code.
- PLAN-10-003 (outline / phase 5): three finalize loop-backs (about 90 minutes of re-dispatched reviews), each on a
  defect phase 5 should have caught: a correction deliverable that did not enumerate every file carrying the wrong
  claim (a diagram and an architecture page), and a new fail-closed check whose "cannot decode" branch had no
  malformed-but-present test, so unparseable was treated as absent. Suggested: outline lists every file carrying the
  claim being corrected; phase 5 asks for one test per "cannot decode" branch of a new security check.
- PLAN-10-005 / -006 (pre-push gate, push freshness): the `pre-push-quality-gate` step made its own source commit
  (an OpenRewrite-inserted TODO replaced by the suppression marker), which moved HEAD past the last validated build
  and needed a manual `freshness-reconcile` decision; a later inline-fix commit was refused with
  `head_not_validated_by_gate` and re-fired security-audit, functional-review, code-review, the gate, push, CI-verify,
  automatic-review and sonar-roundtrip. Related to the PLAN-12-004 and PLAN-20 head-dependent-chain items above.
  Suggested: the gate should not commit source (or should declare the commit and re-validate itself), and inline
  review-bot fixes should batch into one commit.
- PLAN-10-007 (code-review verifier schema): a verifier dispatch wrote `findings_supported` where the workflow reads
  `grading_supported` and the verdict was read as malformed. Only partly confirmed: the surviving artifact has the
  correct key and no log line records the malformed read. Suggested: validate the verdict JSON by script at write
  time, naming the expected key, with one shared schema for the code-review and functional-review workflows.
- PLAN-10-008 (CodeRabbit quota, recurrence of theme 1): the re-review wait burned its full 600 seconds on a quota
  refusal that stated an ETA of 17 minutes, then escalated; the operator chose merge-anyway under
  `barrier-ask-override`. New detail: the unseen delta was only the bot's own two accepted suggestions. Suggested:
  recognise the quota answer, read the ETA, wait if it fits the budget or escalate at once, and record "unseen delta
  = the bot's own suggestions" with the override.
- PLAN-10-009 (argparse, recurrence of theme 3): `architecture --plan-id P search ...` rejected (exit 2); the leaf
  appended `--plan-id` by rote before the verb. One more data point for the router-scoped-flag class; no new
  suggestion.

Landing residue of PLAN-10: `effort` unknown again (runtime stamp unreadable; the model refresh found
`model_unknown`); `total_tokens` spans mixed populations and a phase figure is over-counted by about 125 thousand;
the post-merge CI wait hit its deadline on the non-gating `Run JMH Benchmarks` job (it finished green afterwards);
one ADR candidate was proposed and not created.

## Addendum 2026-11-05: PLAN-23 (2 plan-marshall messages)

Nothing here was sent. A third message (an owed `architecture enrich` hint about `java:S1612`) is epic-local and is
tracked in the epic's Watches.

- PLAN-23-001 (`review_support test-integrity`): the default `--base-ref` is the `origin/main` tip, so on a branch
  that lags a moved `main` the two-dot comparison attributes upstream's own assertion changes to the branch and
  raises a false `weakened_test` (here on `RefreshFailureClassificationTest`, which PLAN-10 #856 had edited). It
  cleared after the rebase. Two more `weakened_test` flags on this plan were judged false positives by every
  verifier (a parameterised merge of three tests over the same inputs, and an `assert true` replaced by a real
  check), and the functional and code reviews were closed by operator override at each head. I read both in the merged
  diff and agree they are false positives. Suggested: anchor the default on the merge-base (three-dot semantics);
  count a parameterised test's rows, not its methods, when comparing assertion totals; treat `assert true` ->
  real assertion as a strengthening.
- PLAN-23-002 (pre-push gate, Maven): the `module-tests` arm is reported as un-gated on every Maven push even though
  the whole-tree `verify` ran the tests (3680 green). Seventh plan with the degraded arm (after PLAN-05, 08, 09, 11,
  12, 21); the same family as PLAN-21-001 and PLAN-11-002. Suggested, in addition to the earlier items: treat a
  resolved whole-tree `verify` that runs the test phase as satisfying the arm for Maven, or have `build-maven` declare
  a `module-tests` canonical, and stop emitting a per-push warning for a configuration that is normal for the build
  system.

Landing residue of PLAN-23: the first `lessons-capture` run did not receive the orchestration verdict and wrote two
lessons to the global store with `--allow-foreign-store` (both were tombstoned and re-sent to the inbox): one more
instance of the lesson-routing gap at the top of this report; the merge went through under a `barrier-ask-override`
because CodeRabbit's one-review-per-hour quota was spent on the previous push; the plan's wall time was about 21.6
hours, mostly review-quota and review-override waits.

## Addendum 2026-11-05 (second): PLAN-13 (5 plan-marshall messages) and PLAN-14 (3 plan-marshall messages)

NOT filed upstream: the report went to the plan-marshall `live-blockers` inbox on 2026-11-05 up to the PLAN-23 addendum
above. This addendum is written for the operator to file if they choose. Statements come from the senders' messages
(PLAN-13 landing 007, lessons 001 to 005; PLAN-14 lessons 001 to 003) and are NOT independently verified, except
where marked. Plugin 0.1.2046.

- **Stand-alone whole-tree `test-compile` stores junk `build-error` findings** (PLAN-13-001; component `build-maven`).
  The reactor cannot resolve the validation test-jar before `package`; the build wrapper auto-stored 130 findings that
  were bulk-rejected. Same artefact PLAN-11 (120 findings) and PLAN-21 reported. Suggested: the wrapper recognises a
  reactor test-jar resolution failure and does not store it.
- **Finalize-step commits leave the change ledger unrecorded** (PLAN-13-002 and PLAN-14-002; `manage-change-ledger`,
  `phase-6-finalize`, `phase-5-execute`). The execute-to-finalize transition refused twice with
  `execute_commits_unrecorded` after security-audit hardening and review fix rounds (PLAN-13), and after an inline fix
  commit (PLAN-14); rows were appended by hand with `manage-change-ledger append --kind change`. Suggested: record the
  row at commit time for every finalize-step commit, or document the explicit call after an inline fix.
- **A non-required third-party check stalls the arms; a quota refusal forced PR recreation** (PLAN-13-003;
  `automatic-review`, `tools-integration-ci`). `license/cla` stayed pending 30+ minutes and alone made the review-arm and
  sonar-arm preconditions return `arm_pending`; CodeRabbit refusals forced closing #859 and opening #861. Suggested:
  classify non-required checks separately; offer a retry-after for the quota path.
- **Merge-queue wait cannot tell a slow queue from a dequeued PR; the post-merge wait blocks on a benchmark**
  (PLAN-13-004; `tools-integration-ci`, `phase-6-finalize`). The first enqueue was ejected when the merge-group
  `e2e-tests / test` job hung to its 30-minute timeout (185 s on the PR head and the second enqueue); the 1800 s
  landing budget ran out with the queue healthy; `checks wait --adaptive` blocked over 600 s on the post-merge
  Performance Benchmark. Suggested: re-check queue membership before declaring the budget spent; exclude post-merge
  benchmark workflows from the wait. (The orchestrator read the same pending JMH job after PLAN-14, PLAN-23 and
  earlier plans.)
- **Usage accounting skewed across the execute/finalize boundary** (PLAN-13-005; `manage-metrics`). About 374k and 363k
  tokens of execute-phase subagent usage landed in the finalize accumulator and a zero-usage dispatch-boundary row was
  recorded. Suggested: flush the accumulator at the phase boundary and refuse a zero-usage row.
- **Argparse rejections from invented verbs and flags** (PLAN-14-001; `manage-findings`, `manage-architecture`): a
  `query` verb on `manage-findings` and undeclared flags on `architecture search`, both already catalogued in the
  agent-behavior rules; recurrence evidence only, same family as the theme 3 items.
- **`weakened_test` (F7) fires on a pure test-file move** (PLAN-14-003; the Q-Gate check that emits F7, the same
  `test-integrity` surface as PLAN-23 lesson 001). The TC-9 move kept both test methods; the detector read the delete
  as a removal and fired repeatedly until the operator accepted it per head. Read by the orchestrator in the merged
  diff: the removed and added classes carry the same two methods and display names. Suggested: pair deleted and added
  files by rename similarity or method set before emitting.

## Addendum 2026-11-06: PLAN-22 (3 plan-marshall messages)

Read from `plan-22-residual-docs-tests-adrs-001.md` and the merged PR #863; NOT sent. Added after the 2026-11-05 filing.

- **`effort` unstamped again, and the refresh cannot recover it** (PLAN-22). `landing-check` reports `complete: false`,
  missing `effort`. The message states why: the plan status carried no `effort` stamp, and `refresh-runtime-stamp` at
  landing returned `refreshed: false, reason: model_unknown`, keeping the start-time self-reported model. Ninth
  producer of this gap in the epic. Suggested: stamp `effort` at phase-1-init from the same source as the model, or let
  the refresh derive it independently of `model_unknown`.
- **An `operator_override` acceptance carries no finding identity.** Both the functional review and the code review were
  closed with `acceptance=operator_override`, "each accepting one residual finding", but the landing message names
  neither finding, and the plan workspace is deleted after finalize, so the accepted findings cannot be read back by
  the orchestrator. Suggested: list the accepted finding ids (or titles) in the landing facts next to `acceptance`.
- **A behaviour change under a docs-only deliverable is not flagged in the PR intent.** The PR body says "two small,
  tested behaviour changes" and names one (`TokenValidator.close`); the second (a refreshed ID token that fails
  validation now quarantines a rotated session in `TokenLifecycleManager`, with a new 178-line test) is only visible in
  the diff and in the CodeRabbit summary. Read by the orchestrator in the merged diff. Suggested: derive the intent's
  behaviour-change list from the realized footprint, not only from the outline.

## Addendum 2026-11-06 (second): PLAN-16 (5 plan-marshall messages)

Read from `plan-16-keycloak-it-assertions-001` to `-005` and the merged PR #868; NOT sent. Added after the PLAN-22 addendum above.

- **Spec authoring: a literal IdP wire value was never checked** (PLAN-16-001). The epic spec said a wrong client secret
  answers `invalid_client`; Keycloak answers 401 `unauthorized_client`, so a test-only plan became a library change.
  Owner of the fix is the `plan-orchestrator` spec review: a claim that names an external wire value (error code, status)
  must be labelled HYPOTHESIS and re-grounded against a run or the IdP's documentation, and a plan asserting an unverified
  outcome should not be scoped as test-only. The orchestrator-side lapse is acknowledged in `epic.md`.
- **Negative-path ITs must assert the server's specific rejection** (PLAN-16-002). Four assertions accepted unrelated
  failures (no `Location`, any 400, a `RuntimeException` supertype, a partial `Location`). Suggested for the
  `junit-integration` and `persona-integration-tester` standards: error code, absence of the success artifact, exact
  exception type, and a complete `Location` comparison.
- **A post-review nitpick commit re-runs every head-bound finalize gate** (PLAN-16-003). Four trivial test-assertion
  changes cost about 1.5 hours. Options the plan lists: batch bot fixes after every bot finished, defer trivial nitpicks
  with a reasoned reply, or scope the gate re-run to a test-sources-only diff.
- **An ADR number fixed at draft time collided with ADRs merged meanwhile** (PLAN-16-004). The draft took 0020; #863
  added 0020 to 0026. Suggested: allocate through `manage-adr` as late as possible and re-check against the base branch at
  finalize, and treat the number as a shared sequence in an epic that lands plans in parallel.
- **A parked ADR draft has no durable home.** The plan left its draft untracked in the main checkout's `.plan/temp/`
  because filing it would have cost another gate cycle (PLAN-16-005). Read by the orchestrator: the file exists there and
  was copied into the ledger tree. Suggested: let `adr-propose` emit the draft into the landing message or the inbox.
- **`effort` unstamped** (tenth producer, same cause as the PLAN-22 item above: `refresh-runtime-stamp` returned
  `model_unknown`).

## Addendum 2026-11-06 (third): PLAN-15 (6 plan-marshall messages)

Read from `plan-15-validation-test-gaps-001` to `-009` and the merged PR #867; NOT sent. Added after the PLAN-16 addendum above.

- **A deliverable that mandates test removal collides with the `weakened_test` check** (PLAN-15-001). The outline asked to delete
  trivial enum tests; the finalize functional review flagged every removal as a blocking finding and the operator reversed the
  deliverable late. Suggested: let the outline declare a sanctioned removal list the check can read, or settle up front whether
  low-value tests may be deleted. Same check family as the PLAN-14 false positive on a pure file move.
- **Acceptance criteria observable only after push make the pre-push functional review refuse its verdict** (PLAN-15-002). "Sonar
  issue gone" and "PR body records X" cannot be evidenced before the push, so the verifier refused twice and the operator closed
  the review by hand. Suggested: mark such criteria post-push at outline time, owned by `sonar-roundtrip` or `create-pr`.
- **A defect-class survey scoped to named sites missed its siblings** (PLAN-15-003). Four named parse sites were fixed; code review
  then found four more, costing a loop-back, two fix tasks and a scope-creep acceptance. Suggested: when the defect is a library call
  pattern, enumerate every call site of the pattern across all modules by content search at outline.
- **Non-discriminating test fixtures** (PLAN-15-005). A tamper helper located `kid` by the first text match and a duplicate-kid test
  put RS256 before RS384 only; both would pass against a simpler wrong implementation and slipped past the in-run code review.
  Suggested for the test-code review checklist: locate structured data by structure, and test an order-sensitive rule in both orders.
- **Finalize-time commits carry no change-ledger row** (PLAN-15-006). An operator-directed commit during functional review had no
  `kind=change` row, so the 5-execute to 6-finalize transition was refused (`execute_commits_unrecorded`). Same cause the PLAN-13
  addendum reports for finalize commits: a second report.
- **`effort resolve-target` rejects `--dispatch-context`** (PLAN-15-007). `lessons-capture.md` says the dispatcher passes the dispatch
  context to the resolve seam; the script's argparse surface does not declare the flag (accepted: apply, caller, default, field, phase,
  plan-id, role, workflow, workflow-gated). One rejected call, retry without the flag succeeded. Doc and script disagree.
- **`effort` unstamped** (eleventh producer, same cause: `refresh-runtime-stamp` returned `model_unknown`).

## Addendum 2026-11-06 (fourth): PLAN-24 (3 plan-marshall messages and 3 landing residues)

Read from `plan-24-client-id-strict-typing-001` to `-005` and the merged PR #876 (`90f8474a`); NOT sent. Added after the PLAN-15 addendum above.

- **`architecture search` has no `--module` flag** (PLAN-24-001). The refine agent carried `--module` over from the sibling verbs
  `commands`, `derived-module` and `files`; `search` declares only `--pattern`, `--content`, `--category`, `--ignore-case`, `--literal`,
  `--pre`, `--pre-ref`, `--plan-id`, `--project-dir`, so argparse rejected the call (exit 2). Suggested: either accept `--module` on
  `search` (a content sweep scoped to one module is a natural request) or have `client-api.md` § search say how to narrow by module.
  Same argparse-rejection family as the PLAN-13/14 verb-paraphrase report.
- **`manage-findings qgate` has no `query` verb** (PLAN-24-002). The outline entry called `qgate query`; the noun registers only `add`,
  `clear`, `list`, `resolve`, `resolve-by-rule`, `resolve-evidenced`. The read verb is `list`. A third verb-paraphrase rejection in this
  epic (`query` here, `get`/`read` earlier): the Canonical invocations block is not being quoted verbatim by the issuing step.
- **Sonar `java:S5778` slips past `verify -Ppre-commit` in new `assertThrows` tests** (PLAN-24-003). The whole-tree verify (3870 tests)
  was green and the smell surfaced only in the post-PR Sonar analysis (`AuthorizedPartyValidatorTest.java:266`), costing a fix commit and a
  second pre-push/CI/review loop. Suggested for `pm-dev-java:junit-core` (test-code review rules): put exactly one call inside the
  `assertThrows` lambda and build every argument in the Arrange block. This repository's own PLAN-23 already fixed S5778 sites, so it recurs.
- **Post-merge `checks wait --adaptive` hung for 10 minutes with no output** (landing residue). The lane stopped it by hand. Suggested: a
  hard wall-clock bound with a progress line, so a silent hang is distinguishable from a long wait.
- **The e2e job was cancelled by its 30-minute job timeout after all 30 tests passed** (landing residue; a teardown hang that the lane says
  recurs on `main`). Cleared with a rerun. Not a plan-marshall defect as such; recorded as a project Watch, and noted here because the
  finalize arms treat a cancelled check and a failed check alike.
- **The pre-push gate recorded "test-compile not run"** (landing residue): the stand-alone test-compile and module-tests arms were
  degraded, the whole-tree verify was green. The gate does not say why the arms degraded.
- **`effort` and `model` self-reported** (twelfth producer with a failed runtime read: `refresh-runtime-stamp` returned `model_unknown`).
  Unlike the earlier landings this one carried `effort=medium`, so `landing-check` reported it complete.

## Addendum 2026-11-06 (fifth): PLAN-18 (7 plan-marshall messages and 3 landing residues)

Read from `plan-18-doc-accuracy-001` to `-008` and the merged PR #877 (`af19b9c2`); NOT sent. Added after the PLAN-24 addendum above.

- **Outline scopes a wording-defect plan to the files the finding names** (PLAN-18-001 and -002). The 4-plan scope-criterion validator
  found the same defect phrase in `commons/transport/package-info.java`, which no deliverable covered, and the plan looped back
  4-plan to 3-outline to 4-plan; the docs-only plan then took three functional-review and two code-review rounds, each finding one more
  sibling of the same claim. Suggested for `phase-3-outline`: for a doc-accuracy or wording-defect plan, run `architecture search --content`
  for the defect phrases and their paraphrases across the whole tree (docs, Javadoc, README, POM descriptions) before fixing the
  affected-files set, and include every hit or record an exclusion; and have the review grep the phrase tree-wide once. The project-owned
  half (the residual sibling claims) is in PLAN-25 item 7(j).
- **A success criterion that names the PR body cannot be evidenced by the pre-push functional review** (PLAN-18-003 and -004). "D-13 is
  recorded against this PR in the PR body" was graded `unevidenced_criterion`, then again as a residual, two blocking findings that each
  needed an operator acceptance for a deliverable that was fine. Suggested: outline should not phrase criteria against artifacts that exist
  only after `create-pr` (put the finding id on `pr_title` or the commit message), or the functional review should classify them as
  deferred to `create-pr`; per-deliverable commit messages should carry the finding ids they fix.
- **A new doc claim ignored a conditional branch in the code it described** (PLAN-18-005, part 1). The rewritten client docs said the
  advertised `introspection_endpoint` scheme is https-checked; `DiscoveryResolver.validateEndpointSchemes` returns early when the issuer is
  not https. sourcery caught it after the PR was opened; four places were reworded. Suggested for `pm-documents:ref-documentation`: when a doc
  states a security check, read every early return and guard in the checking method and state the condition under which it applies.
- **A review-bot quota refusal stalled required-bot participation** (PLAN-18-005, part 2). CodeRabbit's free-tier quota refusal read as
  neither "participated" nor "pending", and progressed only after `review_rate_window_await` was enabled and the bot re-triggered. Seen
  before on PLAN-13, PLAN-14 and PLAN-23 (see the earlier addenda); `automatic-review` should recognise the refusal as its own state.
- **`manage-findings qgate query` again** (PLAN-18-006). Two more `argparse_rejection` calls in one run (exit 2; the registered verbs are
  `add`, `clear`, `list`, `resolve`, `resolve-by-rule`, `resolve-evidenced`). This is the same invented verb as PLAN-24-002 above, so it is
  a live recurrence across two plans: look for prose on the Q-Gate loop-back path ("query the Q-Gate findings") that invites it, or accept
  `query` as a read alias.
- **The remote-merge gate asks about a rebase the merge queue skips** (PLAN-18-007). With `auto_rebase_threshold=no_overlap_only` and
  `use_merge_queue=true` the gate blocked and asked the operator about a clean one-file overlap, although under the merge queue the rebase is
  skipped anyway. Suggested for `phase-6-finalize`: evaluate the threshold only on the path that rebases, or downgrade it to a log line
  under `use_merge_queue=true`.
- **The squash commit on `main` carries the CodeRabbit "Summary by CodeRabbit" block and a second `Co-authored-by` trailer** (landing
  residue). The merge-queue path offers no per-enqueue message override and `ci repo` has no verb for the default merge-message setting.
- **Two phase-6 usage figures were entered as estimates** (about 1,000 and 13,000 tokens) before the real subagent usage arrived, so the
  finalize token total is a slight overstatement (landing residue).
- **`effort` was never stamped** (the twelfth known producer with an unstamped `effort`; `landing-check` `complete: false`); the `emit-landing` and `archive-plan`
  steps had not recorded when the landing was assembled, so they are absent from `steps`.
