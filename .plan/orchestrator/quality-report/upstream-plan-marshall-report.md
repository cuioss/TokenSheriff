# Upstream report for plan-marshall: friction seen in the quality-report epic

epic: quality-report
written: 2026-10-31
plugin version in use: 0.1.2027 (landing messages)
status: NOT sent anywhere. Written for the operator to forward if they choose.

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
