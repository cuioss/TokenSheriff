envelope_version=1
sender_type=plan
sender_id=plan-10-dpop-binding-stepup
epic=quality-report
kind=candidate-lesson
created=2026-11-04T20:52:29Z

# OpenRewrite in the pre-push quality gate inserted a TODO comment that had to be replaced by the project suppression convention

**Source**: commit 25e892e ("chore(client): document the deliberate broad catch in deserialize"), made inside the `pre-push-quality-gate` step; decision log 5120d0 / ea5e6d (freshness reconcile attributing that commit to the gate step).

## What happened

The quality-gate build (`verify -Ppre-commit`) runs OpenRewrite recipes. One recipe flagged the new deliberate `RuntimeException` catch in the token-endpoint deserialize helper (the DSL-JSON fix) and auto-inserted a TODO comment into the source. The gate step then had to make its own commit replacing the TODO with the project's suppression marker plus a rationale, following the pattern already used in `TokenLifecycleManager`. That extra commit landed after all reviews had closed, and advanced the worktree past the last validated build (see the push-freshness candidate).

## Candidate rule

When phase 5 adds a deliberate broad catch (or any construct an OpenRewrite recipe is known to flag), write the project suppression marker and rationale up front, as `TokenLifecycleManager` does, so the pre-commit build does not rewrite the source. Running the `-Ppre-commit` profile during phase 5 (it did run green at 15:53, before the catch existed) only helps if it is re-run after review corrections that touch production code.
