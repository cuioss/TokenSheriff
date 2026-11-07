envelope_version=1
sender_type=plan
sender_id=plan-19-doc-structure-hygiene
epic=quality-report
kind=candidate-lesson
created=2026-11-06T22:43:08Z

# Candidate lesson: ADR conflated local token preservation with server-side validity

- Source: CodeRabbit inline finding on PR #879 (thread PRRT_kwDOOA6qMM60tORa), finding hash 953168, resolution fixed (fix task TASK-8, commit 56710e6)
- File: doc/adr/0010-A_refresh_token_the_authorization_server_declares_invalid_is_a_third_classification_state.adoc:15
- Signal: automated review (review-bot finding remediated in run)

## What slipped

The ADR described the refresh token as preserved after a transport failure or server error without distinguishing local preservation from server-side validity. If the authorization server rotated the refresh token before the response was lost, the locally kept token may already be invalid at the server, so a retry can be refused.

## Possible corrective rule

When documenting a client-side state classification for a non-idempotent exchange (refresh-token redemption), state explicitly what the client knows locally versus what the server may already have applied, including the lost-response and server-error cases.

Classification (global vs epic, actionable vs knowledge) is deferred to the orchestrator.
