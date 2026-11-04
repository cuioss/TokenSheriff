envelope_version=1
sender_type=plan
sender_id=plan-12-benchmark-build
epic=quality-report
kind=candidate-lesson
created=2026-11-01T17:31:06Z

# Workflow docs ahead of the installed executor cause argparse rejections

Signal: script-failure cluster (floor count 1).

## What happened

During 6-finalize the workflow documents read from the plugin cache described plan-marshall 0.1.2040, while the generated `.plan/execute-script.py` executor still mapped the 0.1.2027 scripts. Verbs and flags the docs prescribe did not exist in the installed scripts and failed with argparse rejections (exit 2):

- `pre_merge_gate` `consequence-text` and `record-merge`
- `review_completeness` `roster`

The agent followed the docs exactly, so the "never invent subcommands" rule did not prevent this. The documented verb was real, just not in the executor that ran it.

## Suggested direction

- Detect doc/executor version skew when the workflow is resolved (for example, `resolve-workflow` or the finalize entry compares the bundle version of the resolved doc against the executor's version stamp) and stop with a clear "run marshall-steward upgrade" message, before any step fails partway.
- Or resolve workflow docs from the same bundle version the executor was generated from.

## Component

plan-marshall:extension-api (resolve-workflow) / plan-marshall:marshall-steward (executor staleness)
