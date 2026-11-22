envelope_version=1
sender_type=plan
sender_id=implement-plan-14-closing-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-20T15:11:30Z

# Candidate lesson: ci pr edit consumes the prepared body file, a second edit needs a full re-prepare

- Component: plan-marshall:tools-integration-ci
- Suggested category: improvement
- Observed in plan: implement-plan-14-closing-sweep (verified in-session)

## Observation

`ci pr edit` consumes (deletes) the body file staged by `prepare-body`. A second PR-body edit in
the same run therefore cannot Edit the staged file (the Edit fails because the file is gone); the
caller must run `prepare-body` again and rewrite the full body from scratch.

## Suggested correction

Either keep the last-submitted body available (e.g. re-seed the prepared file from the current PR
body on `prepare-body`) so incremental edits are possible, or document the consume-on-edit
behaviour in the pr edit / prepare-body contract so callers plan a full rewrite.
