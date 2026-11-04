envelope_version=1
sender_type=plan
sender_id=plan-21-residual-behaviour-fixes
epic=quality-report
kind=candidate-lesson
created=2026-11-02T10:27:55Z

# Candidate lesson: pre-push gate test-compile arm cannot succeed for test-jar consumers

Source signal: script-failure cluster (1 failing build notation, plan-marshall:build-maven:maven).

## What happened

The pre-push gate's test-compile arm failed in this Maven multi-module project. The
`token-sheriff-validation` generators test-jar is attached at the `package` phase, so any
module that consumes it as a test-scoped dependency cannot resolve it at `test-compile`
in a reactor build that stops before `package`. `architecture resolve test-compile`
nevertheless exposes a canonical test-compile command for those consumer modules, so the
gate ran a command that cannot succeed by construction.

The failed attempt filed 120 build-error findings (all the same unresolved-test-jar root
cause). They had to be resolved by hand before the run could continue.

## Possible corrective direction (for orchestrator classification)

- Architecture/build-maven: detect test-jar producer/consumer relationships and either
  resolve test-compile for consumers to a goal that reaches `package` for the producer
  (or `-am` with a phase that attaches the test-jar), or mark test-compile as
  unsupported for such modules instead of emitting an unsatisfiable command.
- Findings ingestion: collapse a build failure whose errors share one root cause
  (unresolvable dependency artifact) into one finding rather than 120.

Components: plan-marshall:build-maven, plan-marshall:manage-architecture, phase-6-finalize pre-push gate.
