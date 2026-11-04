envelope_version=1
sender_type=plan
sender_id=plan-08-quarkus-config-cdi
epic=quality-report
kind=candidate-lesson
created=2026-11-02T11:40:24Z

# Candidate lesson: operator config doc for a changed feature drifted from the code (missing .path leaf, fail-fast rules not documented)

**Signal source:** Q-Gate finding aca5b2 (phase 6-finalize, code-review, severity error, resolution taken_into_account, superseded by the code-review round at 59de8916)
**Suggested component:** project-local (token-sheriff-validation-quarkus quarkus-config-doc.adoc), or plan-marshall:phase-4-plan for the planning angle

## What happened

`quarkus-config-doc.adoc:142` listed the JWE multi-key property without the required `.path` leaf, and did not document the new fail-fast rules this plan added to `JweDecryptionConfigResolver` (Q-6). The code changed and the operator documentation for the same feature was not brought along. The finalize code review caught it, which forced a loop-back.

## Candidate rule

When a deliverable changes the validation rules or the property shape of a configuration feature, the same deliverable must update the operator config doc rows and notes for that feature. The outline should list the config doc as an affected file for every deliverable that changes config semantics, not only for the deliverable that was scoped as "docs".
