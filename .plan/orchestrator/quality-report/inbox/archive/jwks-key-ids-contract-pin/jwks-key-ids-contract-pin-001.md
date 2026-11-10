envelope_version=1
sender_type=plan
sender_id=jwks-key-ids-contract-pin
epic=quality-report
kind=candidate-lesson
created=2026-11-10T12:54:24Z

# Candidate lesson: sanitize every untrusted challenge parameter, not only the obvious one

**Source signal:** automated review (CodeRabbit), pr-comment finding `6e3d28`, PR #888, resolved `fixed` by TASK-7 (commit 44d5e01a).

**Component (suggested):** `token-sheriff-client` — `de.cuioss.sheriff.token.client.flow.InsufficientScopeChallengeParser`

## What happened

The new RFC 6750 `insufficient_scope` challenge parser passed `error_description` through `LogSanitizer.sanitize` (escape + length bound) but returned `realm` raw. Both values come from the untrusted `WWW-Authenticate` header of a resource server and can be up to the 8192-character header limit. `BearerChallengeParams.containsOtherControlCharacter` only refuses C0 controls and DEL, so U+0085 (NEL), U+2028 and U+2029 pass the guard and reached the caller through `getRealm()`. This contradicted threat entry `T-CHALLENGE-INJECTION`, which covers header injection through `WWW-Authenticate`.

The defect slipped past implementation, functional review and code review; the review bot caught it after the PR was opened, costing one finalize loop-back (iteration 1/5).

## Corrective rule (candidate)

When a parser returns several attacker-controlled fields from one header, apply the same sanitizer to every returned string field, and add one Unicode line-separator test (U+2028) per field. Do not sanitize only the field that is "meant for display" — any returned field may be logged or shown.

## Classification notes for the orchestrator

ACTIONABLE (defect + corrective action). Project-local Java code, not a plan-marshall component. Closely related to candidate `6ffcc9` (same field, follow-up test gap) — consider merging the two.
