envelope_version=1
sender_type=plan
sender_id=plan-07-public-helper-hygiene
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-15T22:31:15Z

# Candidate lesson: documentation stated guarantees the code does not enforce

**Source signal**: automated review (PR #908, CodeRabbit findings 37a819 and 83b551, both resolved `fixed`).

## What happened

Two documentation passages written in this plan promised bounds that the implementation does not provide. The review bot found both; neither functional-review nor code-review had blocked on them.

- 37a819: ADR-0029, `doc/validation/security-reference.adoc` and the `LogoutTokenValidator` Javadoc said a backward clock step smaller than the freshness window is safe. The replay guard purges an entry once now is past iat plus the window, so a token admitted at t is purged at t+301 (300 s window) and admitted again after a step back to t+299. The advice was reworded to require a clock that never steps backward.
- 83b551: `openid-discovery.adoc` promised unknown-kid key pickup within one plus one further refresh interval. The loader enforces no pickup bound when unknown-kid fetches keep overlapping periodic cycles. Code-review had already noted this as an advisory (decision log 18:54:00Z, "not a bound the loader enforces") but did not block on it.

## Suggested rule (for orchestrator classification)

When a document states a numeric bound or a safety threshold, trace it to the code path that enforces it, including the adversarial or concurrent case. If no code enforces it, state the weaker property. A code-review advisory that says a documented bound is not enforced should be fixed before push, not left as advisory.

## Evidence

- manage-findings pr-comment 37a819 and 83b551 resolution_detail.
- Decision log 2026-11-15T18:54:00Z code-review advisory at openid-discovery.adoc:238.
