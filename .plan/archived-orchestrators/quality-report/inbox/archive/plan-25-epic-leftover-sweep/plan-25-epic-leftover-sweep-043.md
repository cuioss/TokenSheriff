envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:27:12Z

component=plan-marshall:phase-4-plan
category=improvement
created=2026-11-09
source_record=qgate 4-plan 47b611 (module_mapping_validator, accepted)

# Integration-test fixtures in another module trip the module-mapping check

## Observation

Q-Gate finding 47b611: deliverable 21's test task changed six integration-test fixtures in token-sheriff-quarkus-integration-tests (loopback opt-in), while the implementation sat in token-sheriff-client and token-sheriff-client-quarkus. No mapping row covered it. Accepted as intentional.

## Candidate rule

The repository's test-to-implementation module mapping (doc/developer/test-impl-mapping.adoc, now created by D19) should list the integration-test module as a valid test home for the client modules, so the check stops flagging this pairing.
