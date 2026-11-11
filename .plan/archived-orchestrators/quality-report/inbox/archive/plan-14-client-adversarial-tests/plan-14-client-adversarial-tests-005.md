envelope_version=1
sender_type=plan
sender_id=plan-14-client-adversarial-tests
epic=quality-report
kind=landing
created=2026-11-05T13:02:28Z

## What landed

plan-14-client-adversarial-tests shipped as #862 (merged via the merge queue as f29ca9cb, under a recorded barrier-ask-override because the required review bot CodeRabbit was rate-limited).

```landing-facts
schema=landing-facts/1
plan_id=plan-14-client-adversarial-tests
epic=quality-report
pr=#862
merge_state=merged
cleanup_owed=false
deliverables_total=4
deliverables_done=4
total_tokens=21387978
total_wall_seconds=23418.0
harness=claude
model=claude-sonnet-5-5
effort=unknown
plugin_version=0.1.2046
executor_version=0.1.2046
steps=finalize-step-sync-baseline:done,finalize-step-security-audit:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,adr-propose:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done,archive-plan:pending
```

## Residue

- Two production fixes rode with the tests (new findings, named in the PR): `TokenEndpointClient` rejects a duplicate top-level member in a 2xx token response as `RedeemedResponseException` (and treats a 4xx error body repeating a member as a plain `TransportException`); `DiscoveryResolver` refuses non-https endpoints advertised by an https issuer (not relaxed by `allowInsecureHttp`) and requires every Content-Type header line to be `application/json`.
- The validator raised `weakened_test` (F7) as a false positive on the TC-9 file move; the operator accepted it for this plan and each override was bound to a specific HEAD.
- CodeRabbit (required bot) never produced a review (rate limit); the merge proceeded under the operator's `barrier-ask-override` at head 15eb888a. Sourcery reviewed.
- Open advisory gaps left for a dedicated decision: a blank optional endpoint in an https discovery document is refused (fail-closed); a quoted numeric `expires_in` is accepted (pinned as observed); the effort stamp was absent, so `effort=unknown`.
- Possible ADR candidate not filed: discovery refuses non-https endpoints from an https issuer irrespective of `allowInsecureHttp`.
- The post-merge Performance Benchmark (JMH) job was still running when the post-merge CI wait hit its deadline; all other checks were green.
