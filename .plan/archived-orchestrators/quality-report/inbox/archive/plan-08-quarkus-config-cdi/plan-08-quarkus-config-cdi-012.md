envelope_version=1
sender_type=plan
sender_id=plan-08-quarkus-config-cdi
epic=quality-report
kind=landing
created=2026-11-02T11:44:08Z

## What landed

plan-08-quarkus-config-cdi shipped as #850 (merged, squash via the merge queue as ed172e43).

```landing-facts
schema=landing-facts/1
plan_id=plan-08-quarkus-config-cdi
epic=quality-report
pr=#850
merge_state=merged
cleanup_owed=false
deliverables_total=7
deliverables_done=7
total_tokens=31139010
total_wall_seconds=64118
harness=claude
model=claude-sonnet-5-5
effort=medium
plugin_version=0.1.2046
executor_version=0.1.2046
steps=finalize-step-sync-baseline:done,finalize-step-security-audit:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,adr-propose:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done,archive-plan:pending
step.functional-review.deliverables_met=7
step.functional-review.deliverables_met_with_residuals=0
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=3
step.code-review.rules_cited=3
```

## Residue

Items the epic should track that no step recorded as a fact:

- **Closes:** Q-1, Q-5, Q-6, Q-9, D-8/J-8 (PLAN-08 spec). Fail-closed behaviour is unchanged; only configurations that were silently ignored now fail.
- **Held for the maintainer, not decided in this run:** CodeRabbit asked for a native-image fixture with class-level `@BearerAuth` and NO `@RegisterForReflection`, and raised two notes marked unverified about the class-level annotation lookup in a native image when the bean class has no reflection registration. Q-1 was verified in a real native container only with the two registered fixtures. Whether the unregistered case can fail open is untested.
- **Pre-existing, accepted by the operator:** the plain whole-tree `test-compile` (and whole-tree `module-tests`) is red on unmodified main (token-sheriff-client and benchmark-core tests cannot see the validation test-jar before packaging). `verify:quality-gate` (3476 tests) was accepted in its place; the PR body says so.
- **Spec-scope exceptions, operator-approved:** the integration-tests build script and POM property (`-am`, `integration-tests.native-build.phase`) and the corrected JWE rows plus JWE note in `quarkus-config-doc.adoc`.
- **Follow-up outside this plan's surface:** `DpopConfig.java:36` class Javadoc still says the nonce-cache TTL default is 300s; the constant is 360. The spec excludes that file.
- **Advisories left open:** the empty-candidate guard in `ConfigValueParser` (`discoverEnvNameSegments`) has no test reaching it; env-only multi-key JWE configuration is skipped without a startup signal (documented and pinned by a test).
- **ADR candidates not created (4):** env-var issuer discovery anchored on the enabled key; interceptor binding read from the bean class not the ArC subclass; `@DefaultBean` client producers; fail-loud orphaned or unresolvable configuration. Creating them after the push would have re-opened every head-dependent step.
- **Findings-store hygiene:** the pre-merge gate refused on 120 stale pending `build-error` findings (re-opened by dedupe when the pre-existing red `test-compile` re-observed them). They were dispositioned individually (17 fixed, 103 accepted) with a stated reason. The build tooling's dedupe re-opening a finding that a later green build had resolved is worth a look.
- **Review-bot capacity:** CodeRabbit (free OSS tier, one review per hour) shaped the run: the first review was delayed about 19 minutes by its rate limit, and a further fix commit could not be re-reviewed inside the same hour. Sourcery refused on a quota (about 2 days) and is optional.
- **Post-merge CI:** a Run JMH Benchmarks workflow triggered by the merge was still running when finalize moved on; every PR check was green.
