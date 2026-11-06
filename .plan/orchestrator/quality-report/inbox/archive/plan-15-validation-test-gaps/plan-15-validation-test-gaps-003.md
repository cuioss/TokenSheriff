envelope_version=1
sender_type=plan
sender_id=plan-15-validation-test-gaps
epic=quality-report
kind=candidate-lesson
created=2026-11-06T08:14:48Z

# Candidate lesson: a defect-class survey scoped to named sites missed the same defect at sibling parse sites

## Source records

- Q-Gate f7e387 — code-review, DpopProofValidator.java:270: DPoP proof parse leaks ArrayIndexOutOfBoundsException on a truncated JSON escape (fixed by TASK-7, 1798fd75).
- Q-Gate 13ecca — code-review, ParClient.java:147: the client JSON parse sites (PAR, UserInfo, Discovery) leak the same exception (fixed by TASK-8, e2036bc0).
- Q-Gate 9590bb — phase 5-execute scope_creep_warning: files outside declared scope, accepted because the operator chose "fix all four now" at the code-review gate.

## What happened

Deliverable D6 surveyed DSL-JSON truncated-escape handling at four named validation parse sites; all four leaked ArrayIndexOutOfBoundsException and were fixed. Code review then found the identical defect at four more sites the outline never listed: the DPoP proof parser in token-sheriff-validation and three parsers in token-sheriff-client. That cost a loop back to 5-execute, two fix tasks, a full re-run of the finalize review chain, and a scope-creep warning that had to be accepted by hand.

## Candidate rule

When a plan targets a defect class tied to a library call pattern (here: DSL-JSON parsing of untrusted input with a truncated escape), the outline should enumerate every call site of that pattern across all modules (e.g. a content search for the parse entry point) rather than the sites the request happened to name. The defect is in the call pattern, so the scope is the call pattern.

## Project fact worth keeping (TokenSheriff)

DSL-JSON surfaces a truncated escape as ArrayIndexOutOfBoundsException, not as its own parse exception. Every DSL-JSON parse of external input in TokenSheriff must catch and translate it.
