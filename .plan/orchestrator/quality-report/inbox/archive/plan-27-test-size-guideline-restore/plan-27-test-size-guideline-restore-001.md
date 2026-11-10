envelope_version=1
sender_type=plan
sender_id=plan-27-test-size-guideline-restore
epic=quality-report
kind=candidate-lesson
created=2026-11-10T09:09:52Z

# Candidate lesson: weakened_test at ClaimNameTest.java (functional-review)

- Source signal: Q-Gate finding `04a2ea` (phase 6-finalize, rule `functional-review`, severity error, resolution `accepted`)
- Plan: plan-27-test-size-guideline-restore (PR #887)
- Component: plan-marshall:phase-6-finalize

## Record

4 assertions gone from `shouldRefuseExplicitNullOnlyForIdentifierClaims`, `shouldRejectExplicitNullForStrictlyMappedClaim` [fix_kind correction].

## Resolution

Operator accepted: the PLAN-27 spec deliberately reverts the PLAN-25 explicit-null refusal behaviour and replaces these tests with `IdentifierClaimNullEquivalenceTest`, so the removed assertions pinned behaviour that no longer exists.

## Observation for the orchestrator

The mechanical weakened_test detector raised a blocking error on a spec-mandated behaviour reversal. Every one of the five removals in this plan needed an operator close, at both functional-review and code-review (see sibling candidates). Possible lesson: a plan whose spec explicitly reverts prior behaviour could declare the expected test removals up front so the weakened_test gate does not block twice.
