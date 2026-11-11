envelope_version=1
sender_type=plan
sender_id=plan-28-epic-cleanup-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-11T11:54:38Z

# Candidate lesson: parser behaviour change left the specification's refusal list stale

Source: Q-Gate finding 50992d (phase 4-plan, scope_criterion_validator under_coverage, deliverable 8, resolution taken_into_account)

Deliverable 8 changed the STRICT refusal list of the insufficient_scope parser (refuse unquoted scope, refuse Unicode-blank scope). Only `doc/client/requirements.adoc` was an affected file. `doc/client/specification/step-up-and-logout.adoc` lines 109-111 enumerate the same STRICT refusal list and would have gone stale.

Resolution in run: TASK-13 regenerated with step-up-and-logout.adoc as a write-replace step.

Possible rule: a change to an enumerated behaviour set (refusal list, accepted grammar) must content-search the docs for every place that enumerates the set (class name, API type names) and add each to affected files.
