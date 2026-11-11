envelope_version=1
sender_type=plan
sender_id=plan-28-epic-cleanup-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-11T11:54:45Z

# Candidate lesson: sanitiser character-set change missed the ADR that defines the set

Source: Q-Gate finding a86bff (phase 4-plan, scope_criterion_validator under_coverage, deliverable 9, resolution taken_into_account)

Deliverable 9 added the nine bidi controls (U+202A-U+202E, U+2066-U+2069) to `ErrorTextSanitizer`'s unsafe set. ADR-0022 (lines 50-52) enumerates that set and claims to be its single definition, but the ADR was not an affected file.

Resolution in run: TASK-15 regenerated with ADR-0022 as a write-replace step.

Possible rule: when code changes a set an ADR claims to define, the ADR is a mandatory write target. Search `doc/adr/` for the class name.
