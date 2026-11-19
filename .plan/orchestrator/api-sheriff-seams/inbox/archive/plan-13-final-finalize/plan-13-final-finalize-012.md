envelope_version=1
sender_type=plan
sender_id=plan-13-final-finalize
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-19T20:38:22Z

Candidate lesson from plan-13-final-finalize (Q-Gate 4-plan, finding 5a4cc9, module_mapping_validator, resolution accepted).

Finding: test/impl module mismatch for deliverable 4. Task 8 (module_testing) declares production Javadoc/comment files in token-sheriff-validation and token-sheriff-client, while sibling task 7 (implementation) declares only doc/*.adoc and AGENTS.md. The task-8 targets are not test files, so the module_testing profile exercises none of the documentation changes.

Resolution: accepted as intentional routing from the approved outline. Javadoc-only edits are verified by compiling the modules and a phrase search, not by paired tests.
