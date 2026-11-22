envelope_version=1
sender_type=plan
sender_id=plan-15-final-fix-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-21T12:44:36Z

# Candidate lesson: a sweep-based success criterion over a hand-listed file set under-covers

**Signal:** Q-Gate (4-plan, scope_criterion_validator)
**Record:** finding cc2df3 (deliverable-3, info, resolved `taken_into_account` at 2026-11-20T18:41:00Z).

## What happened

D3's criterion said the framing-word sweep, re-run over the 65 named files, finds no framed name that is not listed. The validator ran the sweep as a content search. It returned the 65 files plus `StepUpChallengeParserTest.java` (`shouldDropBlankAcrValuesKeepingMaxAge`), which was in neither Files to survey nor Files expected to mutate. It was added as a read step (TASK-5 step 66) with its reason.

Later in 6-finalize the same class came back. Decision-log residual D3.R4 and false_self_report 56967e record that be9a2550 added a new test DisplayName containing "still". That is a sweep hit outside the 65 files, and the PR body had to list it as a kept hit.

## Why it may be worth carrying

A criterion stated over a fixed, hand-built file list goes stale both ways. The outline list can miss hits that exist now, and later fix commits in the same plan add new hits. Deriving the list from the sweep, and re-running it at each head, keeps the criterion and the PR body honest.
