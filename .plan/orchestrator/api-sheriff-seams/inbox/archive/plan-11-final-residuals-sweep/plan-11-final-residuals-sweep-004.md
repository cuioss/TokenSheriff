envelope_version=1
sender_type=plan
sender_id=plan-11-final-residuals-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-18T11:38:28Z

## Candidate lesson: workflow prescribes a non-existent `git-workflow commit --record-change` flag

- Component: plan-marshall:workflow-integration-git (and the finalize/orchestrator hand-back docs that cite it)
- Suggested category: bug
- Source plan: plan-11-final-residuals-sweep (PR #921, merge d8aba512)

### Observation

The workflow tells the orchestrator to commit hand-back fixes with `git-workflow commit --record-change`. That flag does not exist in the installed version (plan-marshall 0.1.215x), so the documented call fails with an argparse rejection and the agent has to find a different path.

### Proposed rule

Every flag a workflow doc prescribes must exist in the script's live `--help` surface; the doc and the script must be changed together. Either add `--record-change` to `git-workflow commit` or remove it from the hand-back instructions and name the real mechanism. A plugin-doctor invocation check over workflow docs would catch this class.
