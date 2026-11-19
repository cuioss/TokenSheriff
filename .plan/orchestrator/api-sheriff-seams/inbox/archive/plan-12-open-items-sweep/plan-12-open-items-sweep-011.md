envelope_version=1
sender_type=plan
sender_id=plan-12-open-items-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-19T06:29:42Z

# Candidate lesson: phase-5 Step 10a passes --changed-paths-file to manage-change-ledger append, which the installed verb rejects

- Signal: script-failure cluster, notation `plan-marshall:manage-change-ledger:manage-change-ledger` (process observation 2 of the run)
- Marker class: `[ERROR] ... script_failure`, exit_code 2

## What happened

The phase-5-execute Step 10a text tells the leaf to append the per-deliverable change-ledger row with
`manage-change-ledger append --changed-paths-file <file>`. The installed verb declares only `--changed-paths`. Every
per-deliverable commit in the run hit the rejection and retried with `--changed-paths`. In one envelope the retry passed
the paths as separate positional tokens and was rejected a second time ("unrecognized arguments: ...spec.js
...devui-navigation.js").

## Evidence

- Work log 2d2d45 (15:37:09Z, TASK-2), b221f7 (16:53:50Z, deliverable 2), 9fcdaa (17:05:34Z, TASK-7), 0fe563
  (18:44:45Z, TASK-14, args `--kind change --deliverable-id 4 --changed-paths-file ... --task-id TASK-14`): all
  "--changed-paths-file not declared ... retrying with --changed-paths".
- Work log b08a8d (17:09:28Z): argparse "unrecognized arguments" with two e-2-e-playwright paths.

## Observation for the epic

The workflow document and the installed script disagree on this flag; the doc, the script, or both need to change so the
documented call is the accepted one. Five rejections in one plan, each recovered by hand.
