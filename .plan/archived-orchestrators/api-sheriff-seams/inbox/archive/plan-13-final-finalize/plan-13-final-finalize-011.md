envelope_version=1
sender_type=plan
sender_id=plan-13-final-finalize
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-19T20:38:17Z

Candidate lesson from plan-13-final-finalize (Q-Gate 4-plan, finding f9d74e, module_mapping_validator, resolution accepted).

Finding: test/impl module mismatch for deliverable 3. Task 6 (module_testing) declares Java test files in token-sheriff-client and token-sheriff-validation-quarkus, while sibling task 5 (implementation) declares only the JS helper devui-navigation.js (e-2-e-playwright-maven). No row of doc/developer/test-impl-mapping.adoc pairs them.

Resolution: accepted as an intentional grouping from the approved outline. The JS change is not exercised by the Java tests and is verified by npm lint plus the TASK-9 spec change.
