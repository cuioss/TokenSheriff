envelope_version=1
sender_type=plan
sender_id=implement-plan-14-closing-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-20T15:11:21Z

# Candidate lesson: test-integrity counts renamed tests as lost assertions and forgets acceptance per head

- Component: plan-marshall test-integrity check (phase-6-finalize pre-merge gate)
- Suggested category: improvement
- Observed in plan: implement-plan-14-closing-sweep (verified in-session)

## Observation

The test-integrity check reports renamed test methods as lost assertions on every round (a rename
is scored as a deletion plus an addition rather than a move). The operator's acceptance of that
report is not remembered across PR heads, so each new head re-raises the identical finding and
forces the same operator confirmation again.

## Suggested correction

1. Match renamed test methods (same body / same assertion set under a new name) as a move, not a
   loss.
2. Persist an accepted test-integrity verdict keyed by the specific lost-assertion set, and carry
   it to later heads while that set is unchanged, so only a new loss re-prompts.
