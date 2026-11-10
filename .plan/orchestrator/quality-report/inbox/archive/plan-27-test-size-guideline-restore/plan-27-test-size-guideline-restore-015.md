envelope_version=1
sender_type=plan
sender_id=plan-27-test-size-guideline-restore
epic=quality-report
kind=candidate-lesson
created=2026-11-10T09:10:34Z

# Candidate lesson: git-workflow refused source-tree commit in 6-finalize (no_sanction)

- Source signal: script failure cluster `plan-marshall:workflow-integration-git:git-workflow` (`[ERROR] ... script_failure`, exit_code=1, failure_kind=script_internal_failure), work-log entry `25e7a9` at 2026-11-10T07:33:46Z
- Plan: plan-27-test-size-guideline-restore (PR #887)
- Component: plan-marshall:workflow-integration-git

## Record

```
Source-tree commit refused in 6-finalize: no step id from the composed phase-6 manifest, no open loop-back fix task and no review-rubric section-6 inline-correction record. Route the fix through a loop-back to 5-execute (open a fix task), pass --step with a composed phase-6 manifest step id, or record the section-6 inline correction, then retry.
refused: true, reason: no_sanction, phase: 6-finalize, step: null, changed_paths: CLAUDE.md, doc/...
```

## Context

Raised while the wait-region unified triage tried to commit the fixes for the two CodeRabbit findings (`ce1f7a`, `6732c1`) in CLAUDE.md and doc/validation/security-reference.adoc. The run then took loop-back iteration 1/5 and committed the fix (`abef336e`).

## Observation for the orchestrator

The triage commit was attempted without `--step` or a loop-back fix task, so the sanction guard refused it. Possible lesson: the review-bot triage path should pass the composed phase-6 step id (or open the loop-back fix task) before committing a source fix, rather than discovering the guard by failure.
