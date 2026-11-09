envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:27:08Z

component=plan-marshall:phase-3-outline
category=improvement
created=2026-11-09
source_record=qgate 3-outline 4a04c3 (scope_criterion_validator, taken_into_account)

# Hand-picked survey set missed security-relevant headers the criterion covered

## Observation

Q-Gate finding 4a04c3: deliverable 11's criterion ("header line-break variants are a standing attack class for every security-relevant header") was checked by a content search that also hit StepUpChallengeParser, StepUpHandler (WWW-Authenticate) and SenderConstraint (DPoP-Nonce). The survey set did not include them and their tests were not in the mutation set. TASK-26 extended coverage and the executor added the four test files later.

## Candidate rule

For an "every X" criterion, build the survey set from a content search over the criterion's subject and list the search in the outline, instead of naming files by memory.
