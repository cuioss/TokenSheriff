envelope_version=1
sender_type=plan
sender_id=plan-15-final-fix-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-21T12:45:07Z

# Candidate lesson: the 6-finalize commit gate refuses the stepless `fix(review): apply inline review dispositions` commit (no_sanction)

**Signal:** script failure cluster `plan-marshall:workflow-integration-git:git-workflow`
**Record:** work log 10c405 (2026-11-21T11:22:00Z): `script_failure ... git-workflow exit_code=1 ... "Source-tree commit refused in 6-finalize: no step id from the composed phase-6 manifest, no open loop-back fix task and no review-rubric section-6 inline-correction record. ..." error_code: INVALID_INPUT refused: true reason: no_sanction phase: 6-finalize step: null`; followed by dde707 (11:22:42Z, loop-back iteration 2/3).

## What happened

After the second wait-region triage (CodeRabbit comments a9db38 and b09886), the inline review-disposition fix was committed through git-workflow without `--step`. The commit gate refused it because there was no manifest step id, no open loop-back fix task, and no section-6 inline-correction record. The fix then went through as loop-back 2.

## Why it may be worth carrying

The review-disposition commit path (the hook that writes `fix(review): apply inline review dispositions`) does not pass a sanction the 6-finalize commit gate accepts. That path should pass the composed manifest step id (for example the wait-region triage / automatic-review step) or record the inline correction first. Otherwise every in-run review fix hits `no_sanction` before it lands.
