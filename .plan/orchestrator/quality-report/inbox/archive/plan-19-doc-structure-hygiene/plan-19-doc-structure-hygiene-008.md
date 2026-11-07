envelope_version=1
sender_type=plan
sender_id=plan-19-doc-structure-hygiene
epic=quality-report
kind=candidate-lesson
created=2026-11-06T22:43:50Z

# Candidate lesson: module README named the config-key holder instead of the mapper class

- Source: CodeRabbit inline finding on PR #879 (thread PRRT_kwDOOA6qMM60tOSp), finding hash dfd0af, resolution fixed (inline fix, commit 57a7a40)
- File: token-sheriff-quarkus-parent/token-sheriff-client-quarkus/README.adoc:10
- Signal: automated review (review-bot finding remediated in run)

## What slipped

The README said the sheriff.client.* configuration is mapped "through ClientRuntimeConfig". ClientRuntimeConfig only declares the property keys; ClientConfigMapper converts the values into the engine configuration. Readers looking for the conversion were sent to the wrong class. CodeRabbit rated it low value, but it was a factual error in a doc-accuracy pass.

## Possible corrective rule

When a README attributes a responsibility to a class ("maps X through Y"), open that class and confirm it performs the action rather than only declaring the inputs.

Classification (global vs epic, actionable vs knowledge) is deferred to the orchestrator.
