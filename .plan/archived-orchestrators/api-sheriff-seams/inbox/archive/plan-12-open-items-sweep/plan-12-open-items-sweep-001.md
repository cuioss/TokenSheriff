envelope_version=1
sender_type=plan
sender_id=plan-12-open-items-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-19T06:28:09Z

# Candidate lesson: scope-creep check fires on operator-sanctioned fix-task files

- Signal: Q-Gate (5-execute), finding bde58e, rule `scope_creep_warning`, component `plan-marshall:phase-5-execute:scope_creep_check`
- Resolution in run: accepted (2026-11-18T20:49:18Z)

## What happened

The 5-execute scope-creep check reported 6 files outside the declared deliverable lists (threshold 5):
`doc/client/specification/test-strategy.adoc`, `ParClientMalformedResponseTest`, `StepUpHandlerOptionsTest`,
`UserInfoClientMalformedResponseTest`, `BearerScopeValidationBuildStepTest`, `BearerScopeValidationCombinedIndexBuildStepTest`.

Every file came from a change the operator had already sanctioned: TASK-11 (PR 920 wording fix), an accepted test rename,
the per-call-site sibling tests of deliverable 3, and TASK-16 (the fix task the operator chose for CodeRabbit finding 1d18b5).
The declared file lists were written before those decisions and were never widened, so the check could not tell
sanctioned growth from unplanned work.

## Evidence

- Finding bde58e resolution_detail (accepted rationale above).
- Decision log 440caf / 253e4b: the same files resurface as `unrequested_change` advisories in functional review.
- Decision log 5157a4: after the rebase, six `unrequested_change` entries again, "all sanctioned by operator decisions and fix tasks".

## Observation for the epic

Fix tasks added by an operator decision (loop-back or review-bot triage) do not extend the deliverable's declared file set,
so the scope-creep check and the functional-review `unrequested_change` rule each re-raise them and each needs a
hand-written acceptance.
