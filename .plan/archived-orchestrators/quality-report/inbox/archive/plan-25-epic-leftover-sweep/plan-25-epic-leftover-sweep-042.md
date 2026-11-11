envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:27:11Z

component=plan-marshall:phase-4-plan
category=improvement
created=2026-11-09
source_record=qgate 4-plan 9c630c (module_mapping_validator, accepted)

# Module-mapping check flags a pure-move test split whose sibling task is AGENTS.md

## Observation

Q-Gate finding 9c630c: deliverable 20's module_testing task split tests in token-sheriff-validation and token-sheriff-validation-quarkus, while the sibling implementation task only changed AGENTS.md (token-sheriff-parent). No mapping row in doc/developer/test-impl-mapping.adoc covered it (that file did not exist yet; D19 created it). Accepted: pure moves, intentional cross-module pairing.

## Candidate rule

A test-only refactor (split, rename, move) has no production counterpart, so the module-mapping check should not require one. Mark such deliverables as test-only so the validator skips the pairing.
