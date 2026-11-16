envelope_version=1
sender_type=plan
sender_id=jwks-loader-gateway-needs
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-15T11:01:43Z

# Candidate lesson: functional-review inline correction commit refused as unsanctioned in 6-finalize

Source signal: script-failure cluster `plan-marshall:workflow-integration-git:git-workflow`, 2 `[ERROR] ... script_failure` work-log rows (same hash d1cc1e), exit_code=1 script_internal_failure.

## Records

- 2026-11-13T16:21:45Z and 16:22:59Z (6-finalize, functional-review / code-review re-fire after loop-back 1): source-tree commit refused with `reason: no_sanction`, `step: null`. The message: no step id from the composed phase-6 manifest, no open loop-back fix task, and no review-rubric section-6 inline-correction record. The changed paths included the ADR-0030 doc. This was the inline correction of the false self-report on the `unknownKidRefreshWindow` Javadoc / docs (Q-Gate finding 3f8a48). Functional review then returned `loop_back` and later completed `done` at 6f3e8e2a.

## Candidate rule

A review step that applies an inline correction in 6-finalize must either pass `--step` with its composed phase-6 manifest step id on the commit call, or record the section-6 inline-correction first, and then commit. The same refusal hit twice in a row, which means the first refusal's remedy text was not acted on before retrying. The functional-review and code-review workflows could state the required `--step` argument at their commit point.

Component hint: plan-marshall:workflow-integration-git / plan-marshall:phase-6-finalize (functional-review, code-review).
