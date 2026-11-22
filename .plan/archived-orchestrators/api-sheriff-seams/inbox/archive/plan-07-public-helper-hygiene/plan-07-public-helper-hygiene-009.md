envelope_version=1
sender_type=plan
sender_id=plan-07-public-helper-hygiene
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-15T22:32:08Z

# Candidate lesson: verify -Ppre-commit rewrites imports in untouched files, so its freshness stamp does not match the committed tree

**Source signal**: orchestrator-observed during the run, checked against the plan's work log.

## What the record shows

- TASK-024 (client back-channel bounded-read tests) recorded artifact writes for about 60 client test files the task did not set out to change (`ClientNativeReflectionMetadataTest`, `auth/*Test`, `flow/*Test`, `lifecycle/*Test`, `token/*Test` and others; work log 16:08:11Z). The scope was the BackChannelHttp migration and one new test class. This matches the pre-commit profile's import rewrite touching files outside the task.
- At the 5-execute to 6-finalize transition the freshness gate returned stale (work log 16:47:54Z, WARNING): "the last verify -Ppre-commit stamp (worktree_sha fcf8d762) was taken on the import-rewritten tree, not the committed tree (ef35fc39); running per_task verify:module-tests inline to observe the committed tree". Freshness passed only after that non-mutating build on the committed tree (16:50:00Z, basis=ledger-verified).

## Suggested rule (for orchestrator classification)

In this repository the `-Ppre-commit` profile changes source (import order and rewrites), including files the task did not touch. Reverting that churn changes the tree sha, so the freshness stamp from the mutating build no longer matches. After reverting it, plan a non-mutating build (module-tests or verify without the rewriting profile) on the clean tree to get a fresh stamp, or make sure the per-deliverable commit includes the rewrite on purpose. This is a project fact, so it may also belong in the architecture hints for the build.
