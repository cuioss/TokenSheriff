envelope_version=1
sender_type=plan
sender_id=plan-12-benchmark-build
epic=quality-report
kind=candidate-lesson
created=2026-11-01T17:31:38Z

# Pre-commit profile reformats files after the last commit, so a green build goes stale

Signal: script-failure cluster / finalize gate friction (merge-candidate head-binding).

## What happened

The project's pre-commit build profile runs formatters (for example OpenRewrite or the formatter plugins) that rewrite source files as a side effect. When that profile ran as the verify step after the last commit, the build passed but left reformatted tracked files in the working tree. The green result was then bound to a HEAD that no longer matched the tree that would be pushed. Either the reformatted files had to be committed, which moves HEAD and invalidates the verified-at-HEAD record, or they were left dirty and broke the clean-tree and head-binding checks. A related symptom in this run: a stray head stamp on `finalize-step-sync-baseline` made the merge-candidate head-binding check refuse.

## Suggested direction

- Run the reformatting profile before the final commit, as a commit-preparing step, never as the last verification. Or make verify detect a dirty tree after the build and fail with "formatter changed N files, commit them and re-verify", not report green.
- Bind verification records to the tree hash after the build, not to HEAD before it.

## Component

plan-marshall:phase-5-execute / plan-marshall:phase-6-finalize (verify-then-commit ordering, head binding)
