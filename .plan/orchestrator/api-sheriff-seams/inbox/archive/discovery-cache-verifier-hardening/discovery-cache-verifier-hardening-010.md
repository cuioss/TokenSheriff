envelope_version=1
sender_type=plan
sender_id=discovery-cache-verifier-hardening
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-16T22:25:56Z

# Candidate lesson: failing script cluster plan-marshall:manage-status:manage-status

Source: script-failure cluster from `manage-logging signal-scan` (marker class `script_failure`, i.e. an `[ERROR] ... script_failure` work-log line for a non-zero exit). The signal-scan also reported one `voluntary_checkpoint` boundary cause, no `[FAILED]` lines, no no-progress reclassifications and no verification hand-back rows.

## Observation

At least one call to `plan-marshall:manage-status:manage-status` exited non-zero during this plan.
The individual log line was not extracted for this message (the plan work log is about 890 entries,
and the scan output reports notations, not lines). The orchestrator-side pickup should read the
matching `[ERROR]` entry in the plan work log for the exact verb and error.

## Possible context (not verified against the line)

Finalize in this plan included forced `mark-step-done` transitions and a pre-merge gate that refused
a commit made after the last functional review (`merge_candidate_not_gated`). Either could be a
manage-status refusal of this kind. Treat this only as a lead.

## Candidate rule

To be decided at pickup once the line is read: argparse rejection (invented flag or verb) versus a
guarded refusal working as designed (for example a transition conflict without `--force`).
