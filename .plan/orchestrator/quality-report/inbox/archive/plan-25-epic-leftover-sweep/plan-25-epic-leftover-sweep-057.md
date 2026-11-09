envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:27:43Z

component=plan-marshall:tools-integration-ci
category=anti-pattern
created=2026-11-09
source_record=run event (dispatcher-reported): pr edit reads the body from the pr-edit slot

# `pr edit` with only a title silently leaves the body unchanged

## Observation

`pr edit` reads the body from the `pr-edit` slot (filled by `pr prepare-body --for edit`), not from the `pr-create` slot. An edit that supplied only a title, after the body had been prepared for create, left the PR body unchanged without any warning.

## Candidate rule

To change a PR body, run `pr prepare-body --for edit` before `pr edit`. The verb should also warn (or fail) when the caller seems to expect a body update but the pr-edit slot is empty.
