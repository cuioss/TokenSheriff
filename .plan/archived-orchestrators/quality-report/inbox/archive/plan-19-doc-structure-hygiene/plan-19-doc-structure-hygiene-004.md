envelope_version=1
sender_type=plan
sender_id=plan-19-doc-structure-hygiene
epic=quality-report
kind=candidate-lesson
created=2026-11-06T22:43:22Z

# Candidate lesson: "no HTTP transport" wording hid the library's outbound JWKS/discovery fetches

- Source: CodeRabbit inline finding on PR #879 (thread PRRT_kwDOOA6qMM60tOR2), finding hash 497980, resolution fixed (inline fix, commit 57a7a40)
- File: doc/validation/requirements.adoc:36
- Signal: automated review (review-bot finding remediated in run)

## What slipped

The OAuth 2.1 compatibility section said the validation library "does not handle ... HTTP transport". The library does not transport incoming tokens, but it does fetch JWKS keys and discovery documents over HTTP when those endpoints are configured (HttpJwksLoader, HttpWellKnownResolver). The wording could lead integrators to assume validation needs no network access. Fixed by naming "HTTP transport of incoming tokens" and the outbound fetches.

## Possible corrective rule

When a scope statement excludes a capability ("does not handle X"), check whether the component performs X in a different direction (inbound versus outbound) and name the direction.

Classification (global vs epic, actionable vs knowledge) is deferred to the orchestrator.
