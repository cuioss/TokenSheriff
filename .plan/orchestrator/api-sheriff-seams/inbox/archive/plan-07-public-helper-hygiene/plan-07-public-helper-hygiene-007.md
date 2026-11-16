envelope_version=1
sender_type=plan
sender_id=plan-07-public-helper-hygiene
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-15T22:31:53Z

# Candidate lesson: a source commit of PR-review fixes in 6-finalize was refused for lack of a sanction

**Source signal**: script failure cluster, notation `plan-marshall:workflow-integration-git:git-workflow` (failure_kind=script_internal_failure, exit_code=1).

## Record

Work log 2026-11-15T20:43:14Z, right after the wait-region triage of the PR #908 review comments:

```
Source-tree commit refused in 6-finalize: no step id from the composed phase-6
manifest, no open loop-back fix task and no review-rubric section-6
inline-correction record. ...
refused: true
reason: no_sanction
phase: 6-finalize
step: null
changed_paths[8]: - doc/adr/0029-Token... [truncated]
```

The fixes for the review findings (ADR-0029 clock advice and the other fixed comments) were committed after the refusal. The functional-review delta round at 20:43:47Z cites the "review-disposition commit 94b98aec".

## For classification

The commit was first tried without `--step` or a loop-back fix task, even though the commit guard needs one of the three sanctions. The likely gap is that the triage-fix path in the wait region does not tell the committer which sanction applies to review-comment fixes. Check whether this recurs across plans before writing a lesson.
