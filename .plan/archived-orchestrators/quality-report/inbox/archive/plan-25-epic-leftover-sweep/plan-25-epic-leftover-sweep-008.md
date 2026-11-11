envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:23:30Z

component=plan-marshall:phase-6-finalize
category=improvement
created=2026-11-09
source_record=qgate 6-finalize d049eb (functional-review, round 1, accepted)

# weakened_test check flags the HttpJwksLoaderFailureTest pure-move split

## Observation

Q-Gate finding d049eb (`weakened_test at HttpJwksLoaderFailureTest`): 53 assertions were moved to HttpJwksLoaderFailureAdvertisedJwksUriTest by the D20 pure-move split. The operator accepted it.

## Candidate rule

Follow assertions moved to sibling classes in the same diff before reporting a weakened test.
