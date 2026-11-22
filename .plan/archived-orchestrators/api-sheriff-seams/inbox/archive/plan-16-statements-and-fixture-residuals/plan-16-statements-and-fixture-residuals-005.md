envelope_version=1
sender_type=plan
sender_id=plan-16-statements-and-fixture-residuals
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-22T16:29:16Z

Candidate lesson (signal: Q-Gate finding 77d4df, phase 4-plan, module_mapping_validator, resolution fixed)

Observation: the 4-plan Q-Gate found a test/implementation module mismatch for deliverable 4. Task 7 added ClientCompiledJsonParserConfigBootTest in token-sheriff-client-quarkus-deployment, while the only production target was ParserConfig.java in token-sheriff-validation. The outline had chosen this placement on purpose (the validation deployment module has no client on its class path), but no task updated doc/developer/test-impl-mapping.adoc. That document's "How to keep this current" section requires the PR that adds a test class to update the mapping.

Resolution: fixed. With the operator's choice, step 2 was added to TASK-7 to list the new test in the client deployment row, with the cross-module reason.

Possible lesson (project side): in this repository, any task that adds, renames or moves a test class must also update doc/developer/test-impl-mapping.adoc in the same PR. Outline and plan authors should add that step up front instead of waiting for the module-mapping validator to catch it. A related CodeRabbit comment on PR #945 suggested generating that inventory from source; it was deferred as a follow-up.

Component hint: project doc doc/developer/test-impl-mapping.adoc; plan-marshall:phase-4-plan task authoring.
