envelope_version=1
sender_type=plan
sender_id=plan-19-doc-structure-hygiene
epic=quality-report
kind=candidate-lesson
created=2026-11-06T22:43:36Z

# Candidate lesson: TLS guarantee overstated where allowInsecureHttp(true) permits cleartext endpoints

- Source: CodeRabbit inline finding on PR #879 (thread PRRT_kwDOOA6qMM60tOSY), finding hash 8a34da, resolution fixed (fix task TASK-9, commit 56710e6)
- Files: doc/validation/security-reference.adoc:52, plus the S2 and I6 entries in doc/validation/threat-model.adoc
- Signal: automated review (review-bot finding remediated in run)

## What slipped

The security reference and two threat-model entries stated TLS protection for discovery and JWKS endpoints unconditionally. allowInsecureHttp(true) (CleartextEndpoints) lets those endpoints use cleartext HTTP. The fix states the HTTPS default and the cleartext opt-in exception in both documents. The same guarantee was stated in two files, so the finding needed a cross-file fix task rather than an inline edit.

## Possible corrective rule

When a security property is stated as a default, document the opt-out switch next to it, and search every document restating that property (security reference, threat model) so they are corrected together.

Classification (global vs epic, actionable vs knowledge) is deferred to the orchestrator.
