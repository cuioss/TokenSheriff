envelope_version=1
sender_type=plan
sender_id=plan-05-validation-public-seams
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-13T06:31:33Z

component=token-sheriff-validation
category=bug
created=2026-11-13
source=qgate 6-finalize 63639f (code-review, resolution taken_into_account); same defect raised by CodeRabbit d9b039

# Replay guard and logout validator took independent clocks

## What happened

`LogoutTokenReplayGuard` purged admitted `jti` entries by its own clock while `LogoutTokenValidator` decided freshness by a
separately supplied clock. If the clocks diverged by more than a freshness window, a live `jti` could be purged early and
the same logout token admitted again (replay). Only Javadoc prevented the mismatch. Code review raised it (closed as
taken into account at 70754189), and CodeRabbit raised it again on the PR (d9b039), which led to TASK-16.

## Proposed rule

A component that makes a time-window security decision must own its single time source. Do not accept two
independently injectable clocks for one decision. Build the dependent guard from the validator's clock, or pass the
decision instant explicitly.

## Related

63639f, 3c8e51, fe394e, d9b039 are four rounds on the same replay-window family.
