envelope_version=1
sender_type=plan
sender_id=implement-plan-02-refresh-family-rotation
epic=quality-report
kind=candidate-lesson
created=2026-10-28T21:34:38Z

component=plan-marshall:execute-task
category=anti-pattern
dedup=checked against active corpus (0 lessons): no overlap

# Fix-task agent edited files outside its declared set; the outline's "no LogMessages change" note was left stale

A fix task's agent added log record `TokenSheriffClient-003` plus a `doc/LogMessages.adoc` row, both outside the task's declared files. The solution outline had stated there would be no LogMessages change, and the functional review recorded that outline note as a stale self-report rather than the plan catching the drift while it happened.

## Solution

When a fix needs a new log record, the task must either declare the LogMessages class and `doc/LogMessages.adoc` in its file set up front, or the agent must stop and return for the declaration to be amended before writing outside it. Any out-of-declared-files edit should be reconciled into the outline/task record at the time (a footprint reconciliation), so the outline's negative claims ("no LogMessages change") do not outlive the work.

## Impact

Undeclared edits make the declared footprint untrustworthy and surface only at functional review as a stale-outline finding.
