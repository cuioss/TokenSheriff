envelope_version=1
sender_type=plan
sender_id=plan-10-client-and-cache-residuals
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-18T13:07:29Z

# Script-failure cluster: git-workflow commit failures and an installed-bundle version skew

**Signal source:** `manage-logging signal-scan`. Signal 3 is 3 distinct notations from 5 `script_failure` rows. This message covers `plan-marshall:workflow-integration-git:git-workflow`. Work log 12:15:29Z, plus an orchestrator report.
**Component guess:** plan-marshall:workflow-integration-git (git-workflow commit) / plan-marshall:marshall-steward (executor/version staleness).

## What happened

1. **Nothing staged.** At 12:15:29Z the TASK-22 per-deliverable commit, `git-workflow commit --plan-id ... --message-file ...`, failed with exit 1 because nothing was staged. It worked after a manual `git add` of the task files. The leaf expected the commit verb to stage the task's written files itself.
2. **`--record-change` rejected.** The orchestrator reports that `git-workflow commit --record-change` was rejected as unsupported. The installed executor/skill copy was older than the docs being followed. Evidence of the skew from inside this envelope: skills loaded from the installed cache resolved to plan-marshall `0.1.2149`, while `extension_api resolve-workflow` resolved the workflow doc from `0.1.2155`. Two installed versions were active in one session.

## Candidate rule

- `git-workflow commit` with an empty index should return a structured `nothing_staged` error that names the unstaged task files, or else stage the plan task's recorded artifacts. A bare exit 1 is not enough.
- When the loaded skill and the resolved workflow come from different installed bundle versions, report it before the first script call. Today a doc-declared flag missing from the older executor shows up as an argparse rejection halfway through the run. The steward's staleness check, or the executor itself, should flag the version mismatch up front.
