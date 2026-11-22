envelope_version=1
sender_type=plan
sender_id=implement-plan-14-closing-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-20T15:10:58Z

# Candidate lesson: manage-change-ledger append rejects --changed-paths-file documented by phase-5

- Component: plan-marshall:manage-change-ledger (doc side: plan-marshall:phase-5-execute)
- Suggested category: bug (doc/argparse drift)
- Observed in plan: implement-plan-14-closing-sweep (verified in-session)

## Observation

The phase-5-execute workflow documents `manage-change-ledger append ... --changed-paths-file PATH`,
but the `append` verb's argparse does not declare `--changed-paths-file`; the call is rejected with
`unrecognized arguments` (exit 2).

## Suggested correction

Either add `--changed-paths-file` to `append` (file-based path list is the shell-safe form) or
correct the phase-5 workflow to the flag set the verb actually accepts, and add a plugin-doctor
invocation check covering this call site.
