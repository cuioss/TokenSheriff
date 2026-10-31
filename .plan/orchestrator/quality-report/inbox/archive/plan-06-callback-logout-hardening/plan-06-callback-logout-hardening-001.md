envelope_version=1
sender_type=plan
sender_id=plan-06-callback-logout-hardening
epic=quality-report
kind=candidate-lesson
created=2026-10-30T19:09:05Z

component=plan-marshall:automatic-review
category=improvement
created=2026-10-30

# Shared CodeRabbit quota ping-pong between concurrent plans cost about two hours

## Observation

PLAN-06 (PR #840) and PLAN-07 (plan-07-jwks-staleness-timeouts, PR #844) ran their finalize review loops at the same time against the one CodeRabbit account quota. Decision log of plan-06:

- 12:02:52Z - refusal recovery DEFERRED: the coderabbit rate window was held by plan-07 with 2742s remaining; the own re-review trigger for head 0ad8f597 returned a quota refusal (eta 51 min).
- 13:00:34Z - refusal recovery ARMED: claimed the window (renewed), attempts=2/6; "a first wait of about 40 minutes on another plan window ended in a second quota refusal".
- 14:00:26Z - automatic-review finally completed.

So roughly two hours of finalize wall time went to waiting on a quota that the two plans were consuming alternately. Each plan's wait ended just as the other plan's trigger spent the refreshed quota, so the waiting plan was refused again.

## Why it matters

The cross-plan rate-window claim (manage-locks) serializes who drives recovery. It does not reserve the next quota slot for the plan that waited longest. With two or more plans in finalize, a waiter can be starved for several windows.

## Suggested direction

- When a plan defers to another plan's window, queue it FIFO for the next refresh, so its trigger goes first and the holder's next trigger waits.
- Or, at epic level, stagger the finalize review steps of sibling plans that share one review-bot account (orchestrator-side scheduling).
- Report the expected wait (window remaining plus queue position) to the operator once, instead of re-polling.
