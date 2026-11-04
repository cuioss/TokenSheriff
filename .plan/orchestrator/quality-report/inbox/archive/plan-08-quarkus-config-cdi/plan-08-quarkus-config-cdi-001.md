envelope_version=1
sender_type=plan
sender_id=plan-08-quarkus-config-cdi
epic=quality-report
kind=candidate-lesson
created=2026-11-02T11:40:16Z

# Candidate lesson: outline criteria that imply writes to files the architecture inventory does not index

**Signal source:** Q-Gate finding 9f1d49 (phase 3-outline, scope_criterion_validator, under_coverage, resolution taken_into_account)
**Suggested component:** plan-marshall:phase-3-outline (or plan-marshall:manage-architecture)

## What happened

Deliverable 2 carried a success criterion ("no application*.properties under src/test/resources or the integration-test module carries a now-orphaned sheriff.token.jwe.* key; any found is fixed in this deliverable") that implies writes to `.properties` files. Those files were not listed under Affected files. The architecture inventory does not index `.properties` resources, so the outline could not enumerate the write-set, and the Q-Gate flagged the deliverable as under-covered.

## How it was resolved

Phase 4 listed the candidate directories (`token-sheriff-validation-quarkus/src/test/resources`, `token-sheriff-quarkus-integration-tests/src/main/resources`) as conditional-change files in the task descriptions, not as declared steps, and the executor read them directly.

## Candidate rule

When a success criterion says "any X found is fixed", the outline must either enumerate the candidate files (reading them directly when the inventory does not index that file type) or narrow the criterion to "verified none exist". Resource types the inventory skips (`.properties`, other non-source resources) need an explicit direct-read step at outline time.
