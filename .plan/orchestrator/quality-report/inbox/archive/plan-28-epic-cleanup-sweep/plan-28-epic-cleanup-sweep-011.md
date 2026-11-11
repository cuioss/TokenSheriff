envelope_version=1
sender_type=plan
sender_id=plan-28-epic-cleanup-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-11T11:55:37Z

# Candidate lesson: a deliberate test inversion was not declared in the outline's Tests to remove

Source: Q-Gate finding 350da4 (phase 6-finalize, functional-review weakened_test, error, resolution taken_into_account, superseded by the functional review round at 31e39c3d)

One assertion at the base disappeared from `InsufficientScopeChallengeParserTest.ScopeGrammar::shouldAcceptUnquotedScope`. This was the operator-decided inversion into `shouldRefuseUnquotedScope` (STRICT refuses any unquoted scope), not a weakening. The outline declared no Tests to remove, so the test-integrity check flagged it and it needed an operator acceptance.

Possible rule: when a deliverable deliberately inverts or replaces a test (a behaviour reversal), list the retired test identity in the outline's Tests-to-remove declaration. That way the integrity gate passes without an AskUserQuestion round.
