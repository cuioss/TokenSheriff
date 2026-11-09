envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:25:06Z

component=plan-marshall:phase-6-finalize
category=improvement
created=2026-11-09
source_record=qgate 6-finalize dca6eb (code-review, round 2, accepted, reobserved_count 1)

# Code review re-files the IssuerConfigResolverTest split row under a generic "code finding" title

## Observation

Q-Gate finding dca6eb (code review round 2, `code finding at .../IssuerConfigResolverTest.java`): the same 48-assertion pure-move split, now titled "code finding" instead of "weakened_test". It carries reobserved_count 1, so the store saw the earlier observation, but the row still came back as a new finding that needed an operator decision. This was the fourth filing of this split (bb821c, f4bb12, a6b0d6, dca6eb).

## Candidate rule

When a reobservation matches an accepted finding and the file is unchanged, the earlier verdict should apply automatically instead of opening a new decision.
