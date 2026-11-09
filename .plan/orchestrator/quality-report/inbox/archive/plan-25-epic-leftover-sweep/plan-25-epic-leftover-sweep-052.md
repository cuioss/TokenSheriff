envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:27:39Z

component=plan-marshall:phase-6-finalize
category=bug
created=2026-11-09
source_record=script-failure cluster plan-marshall:phase-6-finalize:review_support (signal-scan, script_failure class)

# Script failure cluster: plan-marshall:phase-6-finalize:review_support

## Observation

`manage-logging signal-scan` attributes at least one `[ERROR] ... script_failure` line in this plan's work log to `plan-marshall:phase-6-finalize:review_support`. The dispatcher reports a related run event: the review step's loop-back close needs pending findings, but a re-filed accepted finding comes back `kept` (not pending), so at later heads the only close that works is one with no `--accept`. A close with `--accept` at those heads is the likely failing call.

## For the pickup

Confirm against the work log `[ERROR]` lines. If confirmed, the review close should accept already-kept findings as a no-op instead of failing.
