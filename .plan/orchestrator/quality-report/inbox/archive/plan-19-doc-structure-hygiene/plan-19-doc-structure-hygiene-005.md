envelope_version=1
sender_type=plan
sender_id=plan-19-doc-structure-hygiene
epic=quality-report
kind=candidate-lesson
created=2026-11-06T22:43:29Z

# Candidate lesson: OAuth 2.1 compatibility claim ignored the library's rejection of HMAC-signed tokens

- Source: CodeRabbit inline finding on PR #879 (thread PRRT_kwDOOA6qMM60tOSP), finding hash bdffcd, resolution fixed (inline fix, commit 57a7a40)
- File: doc/validation/requirements.adoc:38
- Signal: automated review (review-bot finding remediated in run)

## What slipped

The requirements stated that tokens issued by OAuth 2.1 compliant servers "validate correctly with Token-Sheriff as-is". OAuth 2.1 permits HS256-signed access tokens, but Token-Sheriff rejects symmetric HS256/HS384/HS512 (RejectedAlgorithms). The fix qualified the claim to require a supported signature algorithm and the library's other validation requirements.

## Possible corrective rule

A blanket compatibility claim against an external spec must be checked against the library's own deliberate restrictions (rejected algorithms, mandatory claims) and qualified where the library is stricter than the spec.

Classification (global vs epic, actionable vs knowledge) is deferred to the orchestrator.
