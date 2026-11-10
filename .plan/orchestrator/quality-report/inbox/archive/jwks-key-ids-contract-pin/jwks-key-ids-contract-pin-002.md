envelope_version=1
sender_type=plan
sender_id=jwks-key-ids-contract-pin
epic=quality-report
kind=candidate-lesson
created=2026-11-10T12:54:33Z

# Candidate lesson: a requirement guarantee needs one test per stated property per field

**Source signal:** automated review (CodeRabbit), pr-comment finding `6ffcc9`, PR #888, resolved `fixed` by TASK-8 (commit b6d413e9).

**Component (suggested):** `token-sheriff-client` — `InsufficientScopeChallengeParserTest`; requirement text in `doc/client/requirements.adoc` (CLIENT-25)

## What happened

After the TASK-7 fix sanitized `realm`, the requirement stated that `realm` and `error_description` MUST be escaped AND length-bounded. The tests covered escaping of `realm` (U+2028) and truncation of `error_description` (`shouldTruncateDescription`), but no test pinned truncation of `realm`. The review bot flagged the gap on the second review pass, costing a second finalize loop-back (iteration 2/5) for a one-test commit, plus a full re-run of the head-dependent gates.

## Corrective rule (candidate)

When a fix adds a field to a "MUST be X and Y" requirement, write the field x property test matrix explicitly (each field, each property) in the same task, instead of copying only the test that motivated the fix. A non-vacuity check (remove the sanitize call, see the test fail) per cell confirms the matrix is real — TASK-8 did this, TASK-7 did not.

## Classification notes for the orchestrator

ACTIONABLE. Follow-up of candidate `6e3d28` (same field, same parser) — the two together show a fix-then-incomplete-test pattern that cost two review rounds. Project-local, not a plan-marshall component.
