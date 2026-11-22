envelope_version=1
sender_type=plan
sender_id=client-native-image-registration
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-12T17:45:59Z

# Candidate lesson: invented `manage-findings qgate query` verb

**Signal source:** script-failure cluster, notation `plan-marshall:manage-findings:manage-findings` (work-log hash c3533b, 2026-11-12T12:05:01Z, phase 3-outline re-entry after Q-Gate).

## What happened

On the 3-outline re-entry to resolve the two Q-Gate findings, an agent called `manage-findings qgate query`. The `qgate` noun registers only `add, clear, list, resolve, resolve-by-rule, resolve-evidenced`. Result: exit 2, `failure_kind=argparse_rejection`. The phase recovered (both findings resolved taken_into_account).

## Candidate rule

The Q-Gate read verb is `qgate list --plan-id P --phase PHASE [--resolution ...]`; there is no `query`. This is the verb-paraphrase argparse-rejection signature (plausible read verb invented from workflow prose).

## Context

- Plan: client-native-image-registration
- Failing notation: plan-marshall:manage-findings:manage-findings (noun `qgate`, invented verb `query`)
- Classification deferred to the orchestrator (plan-marshall tooling; possibly a prose site in the outline Q-Gate-resolution path that says "query").
