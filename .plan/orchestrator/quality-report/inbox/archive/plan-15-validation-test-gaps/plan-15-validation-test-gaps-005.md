envelope_version=1
sender_type=plan
sender_id=plan-15-validation-test-gaps
epic=quality-report
kind=candidate-lesson
created=2026-11-06T08:15:14Z

# Candidate lesson: new tests passed against a trivial wrong implementation (non-discriminating fixtures)

## Source records

- pr-comment 5be512 — CodeRabbit inline on PR #867, JwtTokenTamperingUtil.java:494, resolution fixed (TASK-9, 94fb462b).
- pr-comment 9f23ba — CodeRabbit review body on PR #867, JWKSKeyLoaderTest.java:224-236, resolution fixed (TASK-9).
- pr-comment 48fa86 — Sourcery review body "Approved.", resolution taken_into_account (no concern; context only).

## What happened

Both review-bot findings that slipped past the in-run code review have the same shape: the test code could not tell the intended behavior apart from a simpler wrong one.

- REMOVE_KID found the kid member by the first literal "kid" token. A header such as {"example":"kid","kid":"k1",...} left the header unchanged, so the "missing kid" tamper case was not actually produced. Fixed by scanning JSON member names, with a test for an earlier "kid" value.
- The duplicate-kid loader test put RS256 before RS384 only. It would also pass if the loader always picked RS384. Fixed by adding the reverse order and asserting the RS256 algorithm and key material win.

## Candidate rule

For a test helper that edits structured data, locate the target by structure (member name), not by the first text match, and test it with an input where the text appears elsewhere first. For an order-sensitive resolution rule (first wins, last wins), test both input orders so the assertion fails on an implementation that ignores order. The in-run code review did not catch either; this could become a review-checklist item for test code.
