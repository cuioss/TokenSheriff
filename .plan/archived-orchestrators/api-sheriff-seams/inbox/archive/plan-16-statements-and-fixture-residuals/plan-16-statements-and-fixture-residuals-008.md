envelope_version=1
sender_type=plan
sender_id=plan-16-statements-and-fixture-residuals
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-22T16:29:46Z

Candidate lesson (signal: script-failure cluster plan-marshall:manage-status:manage-status)

Observation: at the 5-execute -> 6-finalize transition (2026-11-22T13:40:01Z), manage-status failed with script_failure exit_code=1 failure_kind=script_internal_failure: "1 of 8 worktree-branch commit(s) since diverging from main carry no kind=change ledger row", naming commit 50f3d9b060b5079a7456817cef1cdb957348efe0. That is the HEAD CodeRabbit later reviewed. One transition attempt earlier (13:22:47Z), the same transition was also refused with BLOCKED worktree_mutated (worktree state not verified against the change ledger).

Related signal in the same run: at 2026-11-22T11:37:35Z, phase-5-execute's `manage-change-ledger append` call was rejected because `--changed-paths-file` is not declared, and was retried with `--changed-paths`. The run's other ledger write path therefore also used a flag the verb does not declare.

Possible lesson: in this run, one commit on the feature branch was made without its kind=change ledger row (the deliverable Step 10a settlement, or a commit path that bypassed `git-workflow commit`), and the gap surfaced only at the phase boundary. Find which writer produced 50f3d9b without a row, and check that the ledger-append examples in phase-5-execute use the declared flag (`--changed-paths`, not `--changed-paths-file`).

Component hint: plan-marshall:manage-status (5-execute end-marker post-condition); plan-marshall:manage-change-ledger; plan-marshall:phase-5-execute Step 10a.
