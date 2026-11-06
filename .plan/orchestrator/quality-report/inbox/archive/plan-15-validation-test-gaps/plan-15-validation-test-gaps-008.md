envelope_version=1
sender_type=plan
sender_id=plan-15-validation-test-gaps
epic=quality-report
kind=candidate-lesson
created=2026-11-06T08:15:50Z

# Candidate lesson: token-sheriff-client tests need the validation test-jar, so a bare Maven test / test-compile cannot verify them

## Source records

- Work log [VERIFY] WARNING e911be (TASK-8): planned verification "test -pl token-sheriff-client -am -Dtest=..." fails at test-compile because the client tests depend on the token-sheriff-validation test-jar, which the test phase does not package. Fell back to "verify -Ppre-commit -pl token-sheriff-client -am".
- Work log WARNING 15e347 and ccc63a (pre-push-quality-gate, both firings): a bare whole-tree test-compile cannot run in this reactor ("package de.cuioss.sheriff.token.validation.test does not exist") and no default-scope module-tests canonical resolves; only the whole-tree verify -Ppre-commit ran. Recorded as honest degradation.

## What happened

Twice in one run, a planned verification command could not run because of a reactor fact: token-sheriff-client consumes test classes from token-sheriff-validation's test-jar, which exists only after package. Phase 4 planned a "test" goal for a client test task, and the pre-push gate's dedicated test-compile and module-tests arms silently degraded to the whole-tree verify on both firings.

## Candidate rule / knowledge

KNOWLEDGE for TokenSheriff (owed architecture hint, module token-sheriff-client, kind tip): "Client tests depend on the token-sheriff-validation test-jar; verify a client test change with verify (or package) -pl token-sheriff-client -am, never a bare test or test-compile goal." Phase 4 should resolve client test verification to a package-reaching goal, and the module-tests / test-compile canonicals for this reactor may need a package-reaching definition so the pre-push gate stops degrading.
