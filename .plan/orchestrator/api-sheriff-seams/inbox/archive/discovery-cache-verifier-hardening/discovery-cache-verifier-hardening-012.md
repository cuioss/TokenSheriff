envelope_version=1
sender_type=plan
sender_id=discovery-cache-verifier-hardening
epic=api-sheriff-seams
kind=landing
created=2026-11-16T22:27:52Z

## What landed

discovery-cache-verifier-hardening shipped as #912 (merged by the merge queue as dcbdcfb6).

```landing-facts
schema=landing-facts/1
plan_id=discovery-cache-verifier-hardening
epic=api-sheriff-seams
pr=#912
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=26547218
total_wall_seconds=69006.0
harness=claude
model=claude-sonnet-5-5
effort=medium
plugin_version=0.1.2133
executor_version=0.1.2133
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done
unresolved_review_threads=unknown
review_body_findings_total=unknown
review_body_findings_handled=unknown
sonar_open_new_issues=0
```

## Residue

- No finalize step records the three review-thread and review-body counts, so they are written `unknown`. What the run itself observed: every CodeRabbit inline thread was answered and resolved before the merge, and the pending-findings store held 0 at the merge barrier. Sourcery (optional) refused every review on quota.
- `sonar_open_new_issues=0` is the roundtrip's confirmed new-code issue count at the PR head; the roundtrip counts issues without hotspots.
- Both the functional review and the code review were closed by operator override, so no verdict or count facts are transcribed. The one blocking row at every head is the same `weakened_test` on `CachingDiscoveryResolverTest::invalidDurations`: the test-integrity pattern `\.assert[A-Z]\w*` counts `import static ...Assertions.assertAll` lines, so an OpenRewrite wildcard-import rewrite reads as a lost assertion. The operator accepted it as a false positive (assertion calls outside imports: 49 on main, 60 on the branch). It will recur on any plan that touches that file until the checker is fixed.
- Advisories left unfixed: the `CachingDiscoveryResolver` Javadoc Logging paragraph says a refusal thrown without a delegate line leaves "only the cache's own DEBUG line", which is untrue for an interrupt; a retrieval-flow.adoc sentence omits that a failed state inside `minRefreshInterval` is answered by the stale policy without a claim; stale commit messages cc95f1cc, 1320b234, 290315e1 and f42762aa (history not rewritten; later commits carry the corrections); suppressed broad catches in unit-test helpers against the AGENTS.md rule; a duplicated `invalidSettings()` table; a copy-pasted test harness.
- Some subagent commits carry `Co-Authored-By: Claude Opus 5.5` where the dictated trailer was Sonnet 5.5.
- The formatter and OpenRewrite churn found along the way was split out and merged first as #909 and #910 (the second for a one-line import fix the pre-commit gate required).
- After the merge, the post-merge CI wait timed out on the non-gating JMH benchmark job only; every other check was green.
- archive-plan runs after this message and is not in the steps list.
