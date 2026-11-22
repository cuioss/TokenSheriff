envelope_version=1
sender_type=plan
sender_id=plan-07-public-helper-hygiene
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-15T22:31:31Z

# Candidate lesson: phase_handshake reported drift on Q-Gate counts captured before q-gate-validation resolved them

**Source signal**: script failure cluster, notation `plan-marshall:plan-marshall:phase_handshake` (failure_kind=script_internal_failure, exit_code=1).

## Record

Work log 2026-11-15T13:07:37Z, during the 3-outline to 4-plan transition:

```
status: drift
phase: 3-outline
override: false
drift_count: 2
diffs: qgate_open_count captured "2" observed "0";
       pending_findings_blocking_count captured "2" observed "0"
```

The outline was created with "Q-Gate: pending" (13:02:02Z), q-gate-validation then ran (13:02:35Z to 13:06:42Z) and resolved the two open findings. The handshake compared against the counts captured before that resolution and reported drift. The plan continued into 4-plan right after (13:07:38Z).

## For classification

The drift goes in the safe direction (open findings went from 2 to 0). Possible causes: the capture is taken before q-gate-validation and is not refreshed afterwards, or the handshake treats any change as drift rather than only an increase. This needs a cross-plan check before it becomes a lesson.
