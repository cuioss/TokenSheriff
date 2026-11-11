envelope_version=1
sender_type=plan
sender_id=plan-27-test-size-guideline-restore
epic=quality-report
kind=candidate-lesson
created=2026-11-10T09:10:16Z

# Candidate lesson: code-review weakened_test at ClaimNameTest.java

- Source signal: Q-Gate finding `1677df` (phase 6-finalize, rule `code-review`, severity error, resolution `accepted`)
- Plan: plan-27-test-size-guideline-restore (PR #887)
- Component: plan-marshall:phase-6-finalize

## Record

weakened_test: 4 assertions gone from `shouldRefuseExplicitNullOnlyForIdentifierClaims`, `shouldRejectExplicitNullForStrictlyMappedClaim` [fix_kind correction].

## Resolution

Operator accepted (same five removals as the functional review at this head): the PLAN-27 spec reverts the behaviour and replaces these tests with `IdentifierClaimNullEquivalenceTest`.

## Observation for the orchestrator

Duplicate of functional-review finding `04a2ea` raised again by code-review at the same head. An operator acceptance at functional-review did not carry over to code-review, so the operator had to close the identical removal twice.
