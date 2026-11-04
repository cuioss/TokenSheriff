envelope_version=1
sender_type=plan
sender_id=plan-09-dpop-parser-hardening
epic=quality-report
kind=candidate-lesson
created=2026-11-04T12:33:07Z

# Candidate lesson: every independent reject condition needs its own test that fails when only that condition is removed

**Source plan:** plan-09-dpop-parser-hardening (PR #851)
**Signal:** review-bot findings 90282a items 2-3 (coderabbit review-body nitpicks, fixed by TASK-15) and b6a5a2 item 2 (coderabbit follow-up, fixed by TASK-18)
**Suggested component:** pm-dev-java:junit-core / plan-marshall:persona-module-tester

## What happened

The review bot found three separate test gaps. In each one, a guard existed in production code but no test failed if the guard was removed:

- **Inner JWS empty signature** (`header.payload.`): the `innerRejections()` matrix in `NonValidatingJwtParserJweTest` had no row for it. The existing trailing-dot test in `EdgeCases` produced a four-part token, which the part-count check rejected before the empty-signature guard ran, so that guard was never exercised. The fixture needed a decodable header and payload to reach the guard.
- **Decode-mode flags:** `shouldRejectTrailingDot` and `shouldRejectEmptySignatureSegment` ran only in normal decode mode, so the quiet and opaque `logWarnings` / `trackSecurityEvents` behaviour was untested for those inputs. The fix ran them across `ParserDecodeMode` with the existing `crossProduct` / `assertAccounted` helpers.
- **Single-zero ECDSA component:** the range check rejects `r == 0` and `s == 0` through separate conditions, but tests covered only all-zero, `r == n` and `s == n`. TASK-18 added `r == 0, s == 1` and `r == 1, s == 0`.

## Reusable rule

For each reject condition a deliverable adds (each `||` arm, each guard, each mode flag), name the one test input that reaches that condition and no earlier one. Check that removing only that condition makes the test fail. Specifically:

- Build fixtures that pass every earlier check (decodable header and payload, correct part count). Otherwise an earlier guard hides the one under test.
- When a combined input (all-zero) trips several conditions at once, add one input per condition.
- When the code under test changes behaviour by mode, run the rejection tests across every mode with the existing cross-product helpers.
