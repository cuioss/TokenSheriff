envelope_version=1
sender_type=plan
sender_id=plan-05-validation-public-seams
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-13T06:31:30Z

component=plan-marshall:phase-5-execute
category=improvement
created=2026-11-13
source=qgate 5-execute 151153 (resolution accepted); recurrence of 21958e

# Scope-creep check re-fires with a grown residual set and needs a fresh disposition each time

## What happened

About an hour after 21958e was accepted, `scope_creep_check` fired again with 11 files (residual_set changed from
395d43514c6f to c8823bc536cb). The new set kept the same four pom files and three earlier-deliverable files, and added
`ClaimMappingException.java` (an earlier fix task), plus `AGENTS.md` and `doc/README.adoc`, which TASK-17 was written to
correct. The finding was accepted again with a near-identical explanation.

## Proposed rule

An accepted residual should carry forward. When a new residual set is a superset of an accepted one, only the delta
should need a disposition. Files named in a fix task's own scope should not count as scope creep.
