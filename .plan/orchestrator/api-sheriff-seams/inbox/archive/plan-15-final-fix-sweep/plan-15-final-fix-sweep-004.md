envelope_version=1
sender_type=plan
sender_id=plan-15-final-fix-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-21T12:44:35Z

# Candidate lesson: a Quarkus boot test in the deployment module needs a test-impl-mapping row to pass module_mapping_validator

**Signal:** Q-Gate (4-plan, module_mapping_validator)
**Record:** finding d6237c (deliverable-5, resolved `taken_into_account` at 2026-11-20T18:40:56Z).

## What happened

D5's module_testing task declared `JwksParserConfigBootTest` in `token-sheriff-validation-quarkus-deployment`. Its sibling implementation task changed `ParserConfig.java` in `token-sheriff-validation`. The validator reported a test/impl module mismatch because `doc/developer/test-impl-mapping.adoc` had no row for that pair. The test stayed where it was (a Quarkus boot test must live in the deployment module). A mapping row (test JwksParserConfigBootTest, impl ParserConfig, reason) was added as a TASK-9 step in the same commit as the test. This needed a 4-plan loop-back (work log: `Phase: 5-execute -> 4-plan` at 18:38:50).

## Why it may be worth carrying

In this repo, any fix in `token-sheriff-validation` that is proven by a Quarkus boot test is a cross-module pair by construction. If the outline declares the `test-impl-mapping.adoc` row up front for such pairs, the 4-plan loop-back is not needed.
