envelope_version=1
sender_type=plan
sender_id=implement-plan-14-closing-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-20T15:11:05Z

# Candidate lesson: git-workflow commit has no --record-change although a workflow doc implies it

- Component: plan-marshall:workflow-integration-git
- Suggested category: bug (doc/argparse drift)
- Observed in plan: implement-plan-14-closing-sweep (verified in-session)

## Observation

A workflow doc implies that the git-workflow `commit` verb records the change-ledger entry via a
`--record-change` flag. The verb declares no such flag; passing it is an argparse rejection, and the
ledger entry has to be recorded through a separate call.

## Suggested correction

Either implement `--record-change` on `commit` or remove the implication from the workflow doc and
state the separate ledger call explicitly.
