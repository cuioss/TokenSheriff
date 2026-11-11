envelope_version=1
sender_type=plan
sender_id=plan-15-validation-test-gaps
epic=quality-report
kind=candidate-lesson
created=2026-11-06T08:15:39Z

# Candidate lesson: effort resolve-target rejects --dispatch-context, which the finalize dispatch contract says it passes

## Source records

- Work log [ERROR] 964530 (2026-11-06T08:12:57Z): script_failure notation=plan-marshall:manage-config:manage-config exit_code=2 failure_kind=argparse_rejection — "`--dispatch-context` is not declared for `plan-marshall:manage-config:manage-config effort resolve-target`: ['apply', 'caller', 'default', 'field', 'phase', 'plan-id', 'role', 'workflow', 'workflow-gated']".

## What happened

While dispatching this lessons-capture step (role post-run-review), the finalize dispatcher called effort resolve-target with --dispatch-context, which the script does not declare. The workflow doc lessons-capture.md states "The dispatcher passes the dispatch context to its effort resolve-target, so the resolve seam emits the [DISPATCH] work-log line". The retry without the flag succeeded three seconds later (the [DISPATCH] line 7217f4 is present), so the cost was one rejected call.

## Candidate rule

Either the documented dispatch-context argument exists on effort resolve-target under a different flag name that the doc should name verbatim, or the doc describes a flag that was never added. The doc and the argparse surface disagree; one of them should be fixed so dispatchers stop guessing the flag.

## Classification left to the orchestrator

plan-marshall doc/script drift (manage-config effort resolve-target vs. phase-6-finalize dispatch prose). Not a TokenSheriff fact.
