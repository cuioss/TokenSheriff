envelope_version=1
sender_type=plan
sender_id=plan-20-rewrite-marker-cleanup
epic=quality-report
kind=candidate-lesson
created=2026-11-01T19:34:14Z

Candidate lesson (source: finalize run history, cost of re-running the steps tied to the PR head commit)

A docs-only follow-up commit was pushed after the pre-push gate. That new head commit forced every finalize step tied to the head commit to run again. The re-run took about one hour, including a stall on CodeRabbit's quota of one review per hour.

Observation: any commit after the pre-push gate, even a docs-only one, restarts the whole chain of head-dependent finalize steps and the bot review wait. Folding late documentation fixes into the commit before the gate avoids this cost.
