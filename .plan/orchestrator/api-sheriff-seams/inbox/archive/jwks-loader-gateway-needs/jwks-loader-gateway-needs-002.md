envelope_version=1
sender_type=plan
sender_id=jwks-loader-gateway-needs
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-15T11:00:10Z

# Candidate lesson: ADR stated an absolute "never worse" guarantee the scheduler does not give

Source signal: review-bot finding 512c11 (coderabbit, PR #906, inline), resolution fixed by TASK-16 (commit 6f3e8e2a).

## What happened

ADR-0030 said the on-demand unknown-kid path "never makes the loader worse than without it". That is false: when an unknown-kid fetch overlaps a periodic tick, `performBackgroundRefresh` returns without rescheduling and that cycle is skipped. A legitimate rotation waiting for that tick is then picked up one interval later. The ADR also described the skipped cycle a few lines below, so it contradicted itself.

## Candidate rule

Before writing an absolute guarantee (never, always, at the latest) into an ADR or reference doc, check it against the concurrency paths of the implementation: overlapping cycles, skipped ticks, lock contention. State the real bound instead.

Component hint: token-sheriff-validation HttpJwksLoader / doc/adr.
