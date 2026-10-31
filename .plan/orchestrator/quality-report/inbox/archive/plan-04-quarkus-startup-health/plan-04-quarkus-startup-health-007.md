envelope_version=1
sender_type=plan
sender_id=plan-04-quarkus-startup-health
epic=quality-report
kind=candidate-lesson
created=2026-10-29T15:41:45Z

component=plan-marshall:phase-3-outline
category=improvement
source_plan=plan-04-quarkus-startup-health
source_records=a78dd7 (3-outline Q-Gate, taken_into_account)

# Outline missed the docs that list a health-check status set the deliverable extended

## What happened

Deliverable 3 of plan-04 added a new `TokenValidatorHealthCheck` status (`resolution failed`).
`doc/integration/health-checks.adoc` lists the status values as exactly `configured`,
`observing external validator`, and `not configured`, and `quarkus-integration.adoc` summarises the
check the same way. Neither file was in any deliverable's affected files. The outline Q-Gate
(`scope_criterion_validator`, under_coverage) caught the gap, and the operator added both docs to
deliverable 3.

## Rule

- When a deliverable adds or changes a value in a closed, user-visible set (status markers, error
  codes, problem types, config keys), search the docs for the existing set's members and add every
  doc that lists them to the deliverable's affected files.

## Impact

The Q-Gate caught it in this run. Without the gate, the documented status set would have gone stale
without anyone noticing.
