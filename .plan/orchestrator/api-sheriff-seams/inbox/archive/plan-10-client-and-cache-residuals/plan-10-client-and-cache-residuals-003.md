envelope_version=1
sender_type=plan
sender_id=plan-10-client-and-cache-residuals
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-18T13:06:58Z

# An operator-decided review-bot fix that contradicts a frozen criterion only hits criteria-integrity at the next delta review round

**Signal source:** criteria-integrity decisions (decision log 2026-11-18T06:25:58Z for D7, 2026-11-18T12:24:40Z for D6) and functional-review loop-backs.
**Component guess:** plan-marshall:phase-6-finalize (automatic-review triage / functional-review criteria-integrity).

## What happened

This happened twice in this plan.

- **D7.** CodeRabbit e1a7a4 pointed out that `getDeclaredFields()` gives no ordering guarantee. The fix (TASK-17) changed `DPOP_SUB_PROPERTIES` from declaration order to sorted order. That contradicted D7's frozen wording ("same four keys in the same order"), so the change needed explicit operator authorization at functional review.
- **D6.** CodeRabbit 23b22b led to an operator-decided fix (TASK-23, commit 303ed9ae). The initial-load retry now checks eligibility and claims `fetchInFlight` in a single `lifecycleLock` critical section. That flatly contradicts D6 C1's frozen wording ("claims fetchInFlight before it re-checks eligibility"). The delta functional-review round flagged the conflict, and a second operator question ("Authorize the criterion change") was needed. The deliverable title and the PR body then had to be rewritten.

## Candidate rule

When triage accepts a review-bot finding whose fix changes behaviour that a frozen deliverable criterion describes, check that criterion at triage time. If the fix contradicts it, ask in the same operator question that decides the fix whether to authorize the criterion change. Pass the approved new wording along with the fix task. Today the conflict only shows up on the next functional-review round, which costs an extra loop-back and a second operator question for a decision the operator has already made.
