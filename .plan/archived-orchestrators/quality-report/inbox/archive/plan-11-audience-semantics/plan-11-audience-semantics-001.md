envelope_version=1
sender_type=plan
sender_id=plan-11-audience-semantics
epic=quality-report
kind=candidate-lesson
created=2026-11-04T17:26:42Z

# Candidate lesson: conditional "add a log identifier if needed" branch under-declares the file footprint

**Source**: Q-Gate finding 835478 (phase 3-outline, scope_criterion_validator, under_coverage, severity warning), resolution: fixed in-run.

## What happened

Deliverable 2's change-per-file entry for StrictStringMapper said: if a new log identifier turns out to be required, add it to `JWTValidationLogMessages` and `doc/LogMessages.adoc` in the same change. Neither `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/JWTValidationLogMessages.java` nor `doc/LogMessages.adoc` was listed under "Files expected to mutate", so that conditional branch would have written outside the declared footprint (caught by the phase-4 declared_set_closure referrer check).

## Fix applied

The outline was changed to commit to reusing an existing log record and event type (no new log identifier, no new event type — same as deliverable 1), and the conditional branch was dropped. Outline re-validated.

## Candidate rule

When an outline deliverable contains a conditional "if X is needed, also change file F" clause, either resolve the condition at outline time (commit to one branch) or declare F in "Files expected to mutate". In this project the recurring case is new log identifiers: a new LogRecord in `*LogMessages.java` always pulls in `doc/LogMessages.adoc` too, so both files go in the footprint together or the deliverable states explicitly that no new log identifier is introduced.

Classification (global vs epic-local) is left to the orchestrator.
