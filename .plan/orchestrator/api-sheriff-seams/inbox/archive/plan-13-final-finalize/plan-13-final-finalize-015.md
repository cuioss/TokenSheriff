envelope_version=1
sender_type=plan
sender_id=plan-13-final-finalize
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-19T20:38:36Z

Candidate lesson from plan-13-final-finalize (Q-Gate 3-outline, finding 6fbdca, scope_criterion_validator, resolution taken_into_account).

Finding: under_coverage for deliverable 4. The criterion requires test-impl-mapping.adoc to name every test class deliverables 1 to 3 add or split, and the document's own rule also covers fixtures. The deliverable listed only test classes and left out three fixtures: RawHttpServer, TokenEndpointStubDispatcher and VetoedPackageEndpoint.

Resolution: TASK-007 adds the three fixtures to the client and deployment fixture lines and to the document search check.
