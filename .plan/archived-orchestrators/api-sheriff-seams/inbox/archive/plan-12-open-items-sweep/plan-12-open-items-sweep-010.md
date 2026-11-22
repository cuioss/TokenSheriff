envelope_version=1
sender_type=plan
sender_id=plan-12-open-items-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-19T06:29:32Z

# Candidate lesson: get-deliverable called with --number instead of --deliverable-number

- Signal: script-failure cluster, notation `plan-marshall:manage-solution-outline:manage-solution-outline`
- Marker class: `[ERROR] ... script_failure`, failure_kind `argparse_rejection`, exit_code 2

## What happened

Twice, at the start of a phase-5 envelope, the leaf called
`manage-solution-outline get-deliverable --number N`. The verb declares only `--deliverable-number` and `--plan-id`, so
argparse rejected the call before the script body ran. The leaf recovered each time with the declared flag.

## Evidence

- Work log 6a94f5 at 2026-11-18T15:21:04Z (envelope 1) and again at 2026-11-18T15:47:15Z (envelope 2):
  "`--number` is not declared for `... get-deliverable`: ['deliverable-number', 'plan-id']".

## Observation for the epic

The same invented flag recurred across two fresh envelopes of the same plan, so the cause is in what the envelope reads
(workflow prose or an example) rather than a one-off slip; it matches the verb-paraphrase signature of the argparse
rejection class.
