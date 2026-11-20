envelope_version=1
sender_type=plan
sender_id=implement-plan-14-closing-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-20T15:11:14Z

# Candidate lesson: phase-6-finalize SKILL mentions qgate --type warning that the verb rejects

- Component: plan-marshall:phase-6-finalize (verb side: plan-marshall:manage-findings)
- Suggested category: bug (doc/argparse drift)
- Observed in plan: implement-plan-14-closing-sweep (verified in-session)

## Observation

The phase-6-finalize SKILL documents a `manage-findings qgate ... --type warning` call. The qgate
verb's `--type` choices do not include `warning`, so the documented call is rejected by argparse.

## Suggested correction

Align the SKILL text with the qgate `--type` vocabulary the verb accepts (or add `warning` to the
vocabulary if that classification is intended), and cover the call site with the
manage-invocation-invalid doctor rule.
