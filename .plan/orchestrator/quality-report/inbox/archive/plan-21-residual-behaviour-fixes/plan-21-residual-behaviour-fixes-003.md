envelope_version=1
sender_type=plan
sender_id=plan-21-residual-behaviour-fixes
epic=quality-report
kind=candidate-lesson
created=2026-11-02T10:28:11Z

# Candidate lesson: a fix commit made by unified triage after the pre-push gate cannot be pushed until the gate re-runs

Source signal: finalize run fact, coupled to the automated-review signal (CodeRabbit follow-up fda655 fixed by TASK-11, commit e33b49bf).

## What happened

The unified triage produced a fix commit after the pre-push gate had already passed for the
earlier HEAD. Because the gate binds to the HEAD it verified, the new commit could not be
pushed until the pre-push gate re-ran against it. The ordering cost an extra gate cycle
(and a full build) late in finalize.

## Possible corrective direction (for orchestrator classification)

- Finalize ordering: when triage is able to produce source commits, either route the
  re-gate automatically as part of the triage fix loop or document that every
  triage-produced commit implies a gate re-run, so the cost is planned rather than
  discovered at push time.
- Consider batching triage fixes (review-bot follow-ups + gate findings) before the gate
  runs so one gate pass covers them.

Components: plan-marshall:phase-6-finalize (pre-push gate, unified triage loop).
