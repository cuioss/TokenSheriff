envelope_version=1
sender_type=plan
sender_id=plan-15-final-fix-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-21T12:44:54Z

# Candidate lesson: ci `--plan-id ... pr view --pr-number N` was rejected with an argparse error

**Signal:** script failure cluster `plan-marshall:tools-integration-ci:ci`
**Record:** work log f60f63 (2026-11-21T10:11:51Z): `script_failure notation=plan-marshall:tools-integration-ci:ci exit_code=2 failure_kind=argparse_rejection detail=python3 .plan/execute-script.py plan-marshall:tools-integration-ci:ci --plan-id plan-15-final-fix-sweep pr view --pr-number 940`.

## What happened

During the review re-fire after the merge-queue ejection, a `pr view` call used the form the execution-context contract prescribes for ci verbs without their own `--plan-id` (router-level `--plan-id` before the verb). It was rejected with exit 2. The record has only the invocation and no argparse message.

Read-only observation in this lessons-capture step: the installed (0.1.2177) `ci --help` lists no `--plan-id` on the router (`usage: ci.py [-h] {pr,checks,issue,branch,repo,org} ...`), and `ci pr view --help` declares only `--pr-number` / `--head`.

## Why it may be worth carrying

Either the executor's router-level `--plan-id` handling and the dispatcher contract disagree, or something else in this call was wrong and the logged detail doesn't show it. Both are worth a look. The failure detail should include the argparse message, not only the command line.
