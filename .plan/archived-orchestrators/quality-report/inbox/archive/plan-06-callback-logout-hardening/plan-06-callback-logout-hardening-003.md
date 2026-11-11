envelope_version=1
sender_type=plan
sender_id=plan-06-callback-logout-hardening
epic=quality-report
kind=candidate-lesson
created=2026-10-30T19:09:29Z

component=plan-marshall:phase-6-finalize
category=bug
created=2026-10-30

# Installed finalize scripts lack verbs and flags that the finalize docs tell agents to call

## Observation

During finalize the plan hit several argparse rejections (exit 2). In each one the caller followed the documented invocation, but the installed script had a different surface. Work log, 6-finalize:

- 19:07:46Z `plan-marshall:phase-6-finalize:pre_merge_gate`: `record-merge` is not a registered verb. Registered: `['merge']`. The branch-cleanup docs tell the caller to record the merge through this verb, and the operator also found that a documented `consequence-text` verb was missing on the same script.
- 10:30:48Z `plan-marshall:phase-6-finalize:ci_complete_precondition resolve`: required flags `--pr-number` and `--worktree-path` were missing from the documented call.
- 10:38:08Z / 10:38:11Z `plan-marshall:phase-6-finalize:ci_verify run`: required `--final-status`, `--provider` and `--wait-outcome` were missing. A value derived from the docs (`wait_s...`) was then rejected as an invalid choice for `--wait-outcome` (choices: completed, deadline_exceeded).

A related but different case: at 18:30:47Z `plan-marshall:manage-findings:manage-findings query` was rejected (`query` is not a registered verb). This one is an invented verb (agent-side), not doc drift.

## Defect shape

The workflow docs (installed bundle 0.1.2040) and the script argparse surfaces in that same bundle have drifted apart. The plugin-doctor `manage-invocation-invalid` walk covers `manage-*` scripts. The phase-6-finalize helper scripts (pre_merge_gate, ci_verify, ci_complete_precondition) were apparently not covered, so the drift shipped.

## Suggested direction

- Add the phase-6-finalize helper scripts to the invocation-vs-`--help` drift check, so every fenced `execute-script.py` call in the finalize workflow docs is validated against the live argparse surface.
- For pre_merge_gate, either implement `record-merge` / `consequence-text` or remove them from the docs and name the replacement verb.
- Give each ci_verify / ci_complete_precondition doc call a canonical-invocation block that lists every required flag and the closed value set of `--wait-outcome`.
