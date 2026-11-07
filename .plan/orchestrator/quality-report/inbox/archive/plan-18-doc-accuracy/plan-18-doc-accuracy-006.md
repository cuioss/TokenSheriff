envelope_version=1
sender_type=plan
sender_id=plan-18-doc-accuracy
epic=quality-report
kind=candidate-lesson
created=2026-11-06T18:45:40Z

# Candidate lesson: invented verb `manage-findings qgate query` (argparse rejection, twice)

**Source record**: script-failure cluster (signal_script_failure_clusters_count=1), notation `plan-marshall:manage-findings:manage-findings`, two `[ERROR] ... script_failure` work-log lines (2026-11-06T14:20:56Z during the phase-3-outline re-dispatch, and 14:33:38Z after the 4-plan Q-Gate, before the 5-execute -> 3-outline loop-back).
**Suggested component**: plan-marshall:manage-findings
**Suggested category**: anti-pattern

## What happened

Both calls failed with exit_code=2, failure_kind=argparse_rejection:
"`query` is not a registered verb for `plan-marshall:manage-findings:manage-findings qgate`: ['add', 'clear', 'list', 'resolve', 'resolve-by-rule', 'resolve-evidenced']".
The caller (orchestrator / outline context reading pending Q-Gate findings) paraphrased the read verb as `query`; the real verb is `qgate list --plan-id P --phase PH [--resolution pending]`.

## Corrective rule

Same verb-paraphrase signature already documented in persona-plan-marshall-agent "Never invent script subcommands". Worth checking whether any workflow doc on the Q-Gate loop-back path (phase-3-outline re-entry, plan-marshall q-gate handling) still says "query the Q-Gate findings" in prose that invites `qgate query`; if so, replace the prose with the literal `qgate list ... --resolution pending` invocation. Alternatively add `query` as an accepted read alias if the alias carve-out allows it. Recurred twice in one run, so it is a live recurrence, not a one-off.
