envelope_version=1
sender_type=plan
sender_id=plan-19-doc-structure-hygiene
epic=quality-report
kind=candidate-lesson
created=2026-11-06T22:43:16Z

# Candidate lesson: ADR stated a startup fail-fast guarantee that an @Alternative validator bypasses

- Source: CodeRabbit inline finding on PR #879 (thread PRRT_kwDOOA6qMM60tORi), finding hash 24f4df, resolution fixed (inline fix, commit 57a7a40)
- File: doc/adr/0026-Orphaned_or_unresolvable_extension_configuration_fails_startup_instead_of_being_ignored.adoc:17
- Signal: automated review (review-bot finding remediated in run)

## What slipped

The ADR's first positive consequence promised that orphaned or unresolvable extension configuration (for example a DPoP property) always fails startup. That holds only for the TokenValidator the default producer builds; an application-supplied @Alternative TokenValidator can ignore the orphaned property. The fix limited the guarantee to the default producer and named the alternative-bean exception.

## Possible corrective rule

When a document states a guarantee enforced by a CDI producer, scope the guarantee to that producer and name the replacement path (@Alternative / @DefaultBean override) that sits outside it.

Classification (global vs epic, actionable vs knowledge) is deferred to the orchestrator.
