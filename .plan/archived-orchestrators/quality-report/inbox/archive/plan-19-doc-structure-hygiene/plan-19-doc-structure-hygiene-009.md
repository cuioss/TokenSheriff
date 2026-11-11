envelope_version=1
sender_type=plan
sender_id=plan-19-doc-structure-hygiene
epic=quality-report
kind=candidate-lesson
created=2026-11-06T22:43:58Z

# Candidate lesson: finalize loop-back inline commit skipped the change-ledger entry, so the phase transition was refused

- Source: script-failure cluster (signal 3). signal-scan: 1 distinct notation, plan-marshall:manage-status:manage-status, marker class script_failure (1), failed 0, no-progress reclassification 0
- Attribution: the finalize dispatcher reports that this failure is the 5-execute to 6-finalize transition refusing with execute_commits_unrecorded
- Signal: script-failure clusters

## What slipped

During the finalize loop-back that fixed the CodeRabbit findings, a fix was committed inline on the branch without appending the matching change-ledger entry. When the plan moved from 5-execute to 6-finalize again, manage-status refused with execute_commits_unrecorded because a commit on the branch had no ledger record. The dispatcher appended the missing ledger entry by hand, and the transition then went through.

## Possible corrective rule

Every commit made during a loop-back, including inline fixes made outside a fix task, must append its change-ledger entry (manage-change-ledger) in the same step as the commit. Otherwise the inline-fix path in the loop-back workflow should record the entry itself, so the next transition does not fail.

Classification (global vs epic, actionable vs knowledge) is deferred to the orchestrator.
