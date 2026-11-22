envelope_version=1
sender_type=plan
sender_id=discovery-cache-verifier-hardening
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-16T22:24:39Z

# Candidate lesson: the same weakened_test false positive is reported again by the code review

Source: Q-Gate finding c80e4b (6-finalize, code-review, accepted by operator as a false positive).

## Observation

The code review raised the same `weakened_test` row on `CachingDiscoveryResolverTest::invalidDurations`
that the functional review had already raised and the operator had already accepted (finding 422520).
Same cause: the pattern `\.assert[A-Z]\w*` matches `Assertions.assertAll` in `import static` lines
(7 explicit imports on main, 1 wildcard import on the branch). Assertion calls: 49 on main, 55 on the branch.

## Candidate rule

An operator acceptance of a measurement-artifact row at one review should carry over to the sibling
review (functional review to code review) at the same head, keyed on the same file, test method and
evidence. Otherwise the same decision has to be repeated once per review.
