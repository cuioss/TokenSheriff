envelope_version=1
sender_type=plan
sender_id=plan-12-open-items-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-19T06:29:16Z

# Candidate lesson: isBeanClassCandidate accepted @Vetoed classes in the deployment scope check

- Signal: automatic review (CodeRabbit), pr-comment finding b115be on PR #925, `TokenSheriffProcessor.java:333`; fixed in run via TASK-18 (commit 97096a5e)

## What happened

After TASK-16 added the combined-index scan, CodeRabbit noted that `isBeanClassCandidate` accepts any concrete,
non-interface class. A concrete `@Vetoed` class is not a CDI bean, yet its class-level `@BearerAuth` was merged with an
inherited method's scopes in `checkMethodLevel`, so an over-bound union could refuse deployment for code that never runs.
CodeRabbit called the refusal pre-existing and non-blocking; triage created TASK-18 and the operator chose "Fix it in this
plan" over Hold and Accept. The fix rejects `@Vetoed` classes, with three tests plus controls and a recorded red proof.
Package-level `@Vetoed` is still not read (advisory D3.R4).

## Evidence

- Decision log f1862d (operator choice), bb0302 (advisory D3.R4), 253e4b.
- Finding b115be reply posted 2026-11-18T22:45:54Z, thread resolved (decision 004e97).

## Observation for the epic

A predicate that decides "is this a bean" in a build step needs the full CDI exclusion set (`@Vetoed`, including the
package level), not only abstract/interface checks; this was the second review-bot round on the same method.
