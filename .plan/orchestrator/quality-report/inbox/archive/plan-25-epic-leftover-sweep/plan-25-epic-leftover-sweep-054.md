envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:27:41Z

component=plan-marshall:phase-6-finalize
category=improvement
created=2026-11-09
source_record=run event (dispatcher-reported): head-dependent verdicts re-invalidated by every fix commit

# Every fix commit after the finalize gates re-runs every head-dependent review

## Observation

In PR #883, each fix commit after the finalize gates invalidated every verdict bound to the head: security audit, functional review, code review and the pre-push gate. No step declares `verdict_inputs`, so a wording-only commit (comments, plan text) cost a full review round each time. This is also why the same weakened_test rows were filed four times.

## Candidate rule

Each head-dependent finalize step should declare the inputs its verdict depends on (for example production sources, test sources, docs). A new head should invalidate a verdict only when the diff since the verdict's head touches those inputs.
