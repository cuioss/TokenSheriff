envelope_version=1
sender_type=plan
sender_id=implement-plan-02-refresh-family-rotation
epic=quality-report
kind=candidate-lesson
created=2026-10-28T21:34:29Z

component=TokenSheriff-build
category=anti-pattern
dedup=checked against active corpus (0 lessons): no overlap

# OpenRewrite (-Ppre-commit) can inject an unanswered TODO marker into committed code

The `-Ppre-commit` OpenRewrite run injected a `/*TODO: Catch specific not RuntimeException ...*/` marker into committed code (`holdsNewerGrant`). Nothing in the build or the earlier review rounds flagged it; only the closing code-review round surfaced it.

## Solution

After any `-Ppre-commit` / OpenRewrite run, scan the diff for newly introduced `TODO` markers (the `search-markers` tooling) and resolve each one (catch the specific exception type, or document why a broad catch is intended) before committing. Treat an OpenRewrite-injected TODO as an unanswered finding, not as formatting noise.

## Impact

Without the post-rewrite scan, an unresolved auto-generated marker ships in committed code and is found only by a late review.
