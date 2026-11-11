envelope_version=1
sender_type=plan
sender_id=plan-10-dpop-binding-stepup
epic=quality-report
kind=candidate-lesson
created=2026-11-04T20:52:10Z

# Finalize review loops re-opened three times before the PR, each on a defect phase 5 should have caught

**Source**: 6-finalize Q-Gate findings 016ccd (functional-review), 8b9c46 and 709c40 (code-review); work log loop_back records at 16:39:42, 16:53:50 and 18:02:31 (2026-11-04).

## What happened

Three blocking findings each forced a loop-back, a correction commit, and a re-fire of security-audit, functional-review and code-review (each re-fire is a full author+verifier round over 4 deliverables / 23 files):

1. **016ccd (functional-review, loop 1, fix f2b9b09)** - deliverable 3 was supposed to correct the claim that PAR is a step of the built-in authorization-code flow (C-8c), but `doc/client/architecture.adoc` (lines 54-55, 91) and `doc/resources/diagrams/client-auth-code-pkce.svg` were left untouched. The deliverable's own target file was missed.
2. **8b9c46 (code-review, loop 2, fix 7cbeaf6)** - the new DPoP issuance check accepted a JWT access token whose `cnf` was malformed (`"cnf":"abc"` or `"cnf":{"jkt":["x"]}`): deserialization threw, `decodeConfirmation` returned empty, the token was treated as undecodable and the `jkt` comparison was skipped, while the same payload without `cnf` was rejected. A fail-open branch in a new security check.
3. **709c40 (code-review, loop 3, fix 3f1ab1b)** - see the separate DSL-JSON candidate.

Loop 1 cost about 15 minutes; loops 2 and 3 about 75 minutes of re-dispatched reviews, before the PR existed.

## Candidate rules

- A correction deliverable should enumerate every file carrying the wrong claim (text and diagrams) as affected files at outline time, and phase 5 should verify the claim is gone from each one.
- For a new fail-closed security check, phase-5 tests must cover each "cannot decode" branch with a malformed-but-present input and assert rejection, so "unparseable" is never silently treated as "absent".
