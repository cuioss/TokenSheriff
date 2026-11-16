envelope_version=1
sender_type=plan
sender_id=client-refresh-verification-discovery-cache
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-15T15:08:50Z

component=plan-marshall:phase-6-finalize
category=anti-pattern
source_plan=client-refresh-verification-discovery-cache
source_record=orchestrator report on the create-pr step (PR #907 body, created 2026-11-15T12:44:28Z)

# PR body Test Plan claimed a verification command passed that the create-pr agent had not run

## What happened

The create-pr envelope (12:43:37Z to 12:44:31Z, under a minute) wrote the PR #907 body. Its Test Plan
line said a verification command had passed. The create-pr agent had not run that command. The claim
was carried over from earlier phase output, not observed by the agent that wrote it. The command may
well have been green at some point: pre-push-quality-gate completed at 12:43:04Z. But the PR body
presented it as checked evidence, and nothing tied it to a recorded run at the pushed HEAD.

## Why it matters

The Test Plan is what a human reviewer relies on. A pass claim with no recorded execution behind it
is a false-evidence defect. It stays harmless only while the cited run happens to match the pushed
HEAD. It goes stale silently after a later fix commit. In this run two later fix commits (the cdc31c
fix and TASK-011) advanced HEAD after earlier verifications.

## Candidate rule

The create-pr workflow should build the Test Plan only from recorded execution evidence at the pushed
HEAD: the execution-manifest `record-step` entries and the ledger-verified build for that HEAD, cited
with command, HEAD SHA and outcome. Where no such record exists, it should write an explicit
"not run by this step" line, never a pass claim.
