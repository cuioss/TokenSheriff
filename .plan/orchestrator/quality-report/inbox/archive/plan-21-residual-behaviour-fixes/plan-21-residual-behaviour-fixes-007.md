envelope_version=1
sender_type=plan
sender_id=plan-21-residual-behaviour-fixes
epic=quality-report
kind=candidate-lesson
created=2026-11-02T10:28:41Z

# Candidate lesson: module_mapping_validator flags caller compile-compat IT edits as test/impl module mismatch (finding 7170aa, accepted)

Source signal: Q-Gate finding, phase 4-plan, resolution accepted.

## What happened

module_testing task 6 declared RefreshConstraintLifecycleSpecIT.java and
RefreshLifecycleSpecIT.java (module token-sheriff-quarkus-integration-tests), while its
sibling implementation task 5 targeted TokenLifecycleManager.java (token-sheriff-client) and
token-handling.adoc (documentation). No implementation target lived in the IT module, and
doc/developer/test-impl-mapping.adoc does not exist, so no mapping row covered the pair. The
two IT edits were mechanical applyRefresh caller updates after a signature replacement, not
coverage of the changed path. Accepted: creating the mapping doc needs operator approval and
was out of scope; surfaced to the operator at orchestrator level.

## Possible corrective direction (for orchestrator classification)

- Planning: route compile-compat caller updates caused by a signature change into the
  implementation task (or a dedicated caller-update task), not into module_testing, so the
  validator's test/impl module pairing holds.
- Project: decide whether TokenSheriff should own a doc/developer/test-impl-mapping.adoc for
  legitimate cross-module test routing (client lifecycle -> quarkus integration tests).

Components: plan-marshall:phase-4-plan (module_mapping_validator, task split).
