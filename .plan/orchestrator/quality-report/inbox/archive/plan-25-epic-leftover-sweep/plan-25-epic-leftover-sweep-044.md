envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:27:13Z

component=plan-marshall:phase-4-plan
category=improvement
created=2026-11-09
source_record=qgate 4-plan 045258 (module_mapping_validator, accepted)

# Shared MockWebServer dispatchers in the validation module trip the module-mapping check

## Observation

Q-Gate finding 045258: deliverable 22's test task changed the shared fixtures TokenDispatcher, ParDispatcher, UserInfoDispatcher and AdversarialResponses in token-sheriff-validation, while the implementation was in token-sheriff-client. These dispatchers serve the client tests. Accepted as intentional.

## Candidate rule

Add a row to doc/developer/test-impl-mapping.adoc covering the shared validation-module test fixtures used by token-sheriff-client tests.
