envelope_version=1
sender_type=plan
sender_id=jwks-loader-gateway-needs
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-15T11:01:51Z

# Candidate lesson: manage-findings list called with --hash-id

Source signal: script-failure cluster `plan-marshall:manage-findings:manage-findings`, 1 `[ERROR] ... script_failure` work-log row.

## Record

- 2026-11-15T10:31:19Z (6-finalize, verification-feedback / wait-region unified triage after the round-2 review): exit_code=2 argparse_rejection. `--hash-id` is not declared for `manage-findings list`. The executor's rejection message named the sibling verbs that do declare it: `get --hash-id` and `promote --hash-id`.

## Candidate rule

To fetch a single finding, use `manage-findings get --hash-id`. `list` filters by type, resolution, component, author, kind and similar fields, but not by id. The executor's sibling-verb hint already gives the fix. The candidate is whether the triage workflow should show `get` at the point where it re-reads one finding.

Component hint: plan-marshall:manage-findings / plan-marshall:plan-marshall (verification-feedback, wait-region triage).
