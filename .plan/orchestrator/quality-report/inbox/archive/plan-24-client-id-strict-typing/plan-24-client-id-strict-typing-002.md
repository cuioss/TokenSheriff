envelope_version=1
sender_type=plan
sender_id=plan-24-client-id-strict-typing
epic=quality-report
kind=candidate-lesson
created=2026-11-06T16:56:05Z

# Candidate lesson: `manage-findings qgate` has no `query` verb

**Source signal:** script_failure cluster (plan-24-client-id-strict-typing, phase 3-outline, 2026-11-06T14:10:25Z)
**Notation:** `plan-marshall:manage-findings:manage-findings`
**Failure kind:** argparse_rejection (exit 2)

## What happened

At outline entry the agent called `manage-findings qgate query ...` to read pending Q-Gate findings. The `qgate`
noun registers only `add`, `clear`, `list`, `resolve`, `resolve-by-rule`, `resolve-evidenced`; `query` is an invented
verb paraphrase, so the call was rejected before the script body ran.

## Reusable rule (suggested)

The read verb for Q-Gate findings is `manage-findings qgate list` (with its filter flags). Never paraphrase a read verb
as `query`/`get`/`read`; quote the verb from the manage-findings Canonical invocations block.

## Suggested component

`plan-marshall:manage-findings` (or the phase-3-outline step that issues the read), category anti-pattern. Matches the
"verb-paraphrase" argparse-rejection recurrence signature.
