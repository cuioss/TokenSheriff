envelope_version=1
sender_type=plan
sender_id=plan-17-javadoc-examples
epic=quality-report
kind=candidate-lesson
created=2026-11-06T20:45:28Z

component=plan-marshall:workflow-integration-git
category=bug
title=An inline finalize commit leaves no change-ledger row, and a commit with nothing staged reports FETCH_FAILURE

## Problem

Two separate defects hit the same step in PLAN-17.

1. `git-workflow commit` with nothing staged failed with `git commit failed: exit 1` and a FETCH_FAILURE classification. The real cause was an empty index, so the message pointed at the wrong layer. Staging the three files by explicit path and retrying worked.
2. A code-review fix committed inline during finalize (b252595f) wrote no `manage-change-ledger` row. The `5-execute` to `6-finalize` transition then refused with `execute_commits_unrecorded`. The workaround was reading the real paths with `git diff-tree` and appending `--kind change --deliverable-id 2` by hand, then re-capturing the handshake.

## Rule

`git-workflow commit` should report an empty index as such, and a commit made through it during a loop-back should append its own ledger row, or the transition guard should recognise a commit made through the sanctioned verb.

## Evidence

PLAN-17 work log, commit b252595f.
