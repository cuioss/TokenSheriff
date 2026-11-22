envelope_version=1
sender_type=plan
sender_id=plan-05-validation-public-seams
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-13T06:31:34Z

component=token-sheriff-validation
category=bug
created=2026-11-13
source=qgate 6-finalize 3c8e51 (code-review, resolution fixed by TASK-17 6ab00172)

# Replay guard re-read the clock after the iat freshness decision

## What happened

After the first clock fix, the guard still read the clock again after the validator's `iat` freshness decision. A replay
at the `iat + window` edge could be judged fresh at one instant and admitted against a later one. The fix: the guard holds
no clock, and `admit` takes the validator's single decision instant (6ab00172), with a straddling-clock regression test.

## Proposed rule

Read time once per security decision and pass that instant to every step that depends on it. Re-reading the clock inside
a later step creates an edge window.
