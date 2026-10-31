envelope_version=1
sender_type=plan
sender_id=plan-07-jwks-staleness-timeouts
epic=quality-report
kind=candidate-lesson
created=2026-10-30T16:55:28Z

# Candidate lesson: fence every closed-generation side effect in HttpJwksLoader, not only the publish

**Source signal**: automated review (CodeRabbit, PR #844) - 7 pr-comment findings resolved `fixed` (hash ids a93394, 8dd0f1, 2db329, 8f275c, 9f45bb, 09dc75, 3743c6); automatic-review looped back twice (fix tasks TASK-9..TASK-12).

**Component**: token-sheriff-validation - `de.cuioss.sheriff.token.validation.jwks.http.HttpJwksLoader` (plus `IssuerConfigCache`, `AccessTokenValidationPipeline`).

## What slipped

The plan introduced an epoch/generation fence (`cycleEpoch`, `isLive`, `adoptResolution`) so that a refresh cycle started before `close()` publishes nothing. The fence covered the publish path but not every side effect of the cycle. CodeRabbit (and, independently, the in-run code-review verifier: decision-log F1 at HttpJwksLoader.java:885-889) found:

1. The `finally` block of a failed refresh cycle incremented `consecutiveRefreshFailures` without checking `isLive(cycleEpoch)`; `close()` resets the counter, so a stale cycle's increment counted toward the new generation and could trigger re-discovery one failure early (a93394).
2. When `close()` ran during re-discovery, `adoptResolution` returned `false` but `rediscoverEndpoint` returned the old endpoint, and the cycle still ran the egress check and a blocking fetch - contradicting the Javadoc "a closed generation issues no fetch" (2db329).
3. An unusable advertised `jwks_uri` made `config.getHttpHandler(String)` throw `IllegalArgumentException`/`IllegalStateException` out of re-discovery, skipping the retained-endpoint fetch (8dd0f1).
4. The rejected-re-discovery suppression flag (`rediscoverySuppressed`) had no lift while the retained endpoint kept failing - the loader never consulted discovery again (09dc75, Major); fixed with a bounded lift after 3 further failed cycles.
5. Staleness degrade (`UNDEFINED`) was bypassed by the access-token cache hit path when no custom rules were configured, and by `IssuerConfigCache.resolveConfig` returning an awaited OK future's config without re-checking loader status (8f275c, Major, security).

## Candidate rule

When adding a generation/epoch fence (or a "degraded => reject" contract), enumerate EVERY side effect of the fenced cycle (counter mutations, fetches, egress checks, flag sets) and EVERY read path that can serve the guarded state (caches, awaited futures), and gate or test each one. A javadoc claim "X never happens after close/degrade" must have a mechanism at every site, and a test per site (a mutation check like "remove the reset -> test fails", as TASK-12 did, pins it).

## Evidence pointers

- `manage-findings list --type pr-comment --resolution fixed` on plan-07 (7 rows, fix tasks 9, 10, 11, 12).
- Decision log 10:18:04Z code-review verifier F1-F4 (same counter defect found in-run before the bot).
