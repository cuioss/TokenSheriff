envelope_version=1
sender_type=plan
sender_id=plan-16-statements-and-fixture-residuals
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-22T16:29:36Z

Candidate lesson (signal: script-failure cluster plan-marshall:manage-solution-outline:manage-solution-outline)

Observation: argparse rejected the same call twice in phase 5-execute, once at the first execute entry (2026-11-21T17:19:51Z) and again at the re-entry (2026-11-22T11:54:18Z). Both times the log read: script_failure exit_code=2 failure_kind=argparse_rejection, "`--number` is not declared for `manage-solution-outline get-deliverable`: ['deliverable-number', 'plan-id']".

The same invented flag came back on a fresh execute envelope, so the wrong form is reproducible from the phase-5-execute / execute-task instructions or their examples, not a one-off slip.

Possible lesson: the `get-deliverable` selector flag is `--deliverable-number`, never `--number`. Check whether a phase-5-execute or execute-task workflow line, or an example, shows `--number`, and point those call sites at the manage-solution-outline Canonical invocations block.

Component hint: plan-marshall:manage-solution-outline (get-deliverable); plan-marshall:phase-5-execute.
