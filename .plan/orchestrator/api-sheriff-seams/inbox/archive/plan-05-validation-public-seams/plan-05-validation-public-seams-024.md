envelope_version=1
sender_type=plan
sender_id=plan-05-validation-public-seams
epic=api-sheriff-seams
kind=landing
created=2026-11-13T06:33:25Z

## What landed

plan-05-validation-public-seams shipped as #899 (merged).

```landing-facts
schema=landing-facts/1
plan_id=plan-05-validation-public-seams
epic=api-sheriff-seams
pr=#899
merge_state=merged
cleanup_owed=false
deliverables_total=8
deliverables_done=8
total_tokens=39315801
total_wall_seconds=84055.0
harness=claude
model=claude-sonnet-5-5
effort=medium
plugin_version=0.1.2106
executor_version=0.1.2106
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done
unresolved_review_threads=0
review_body_findings_total=1
review_body_findings_handled=1
sonar_open_new_issues=0
step.functional-review.deliverables_met=8
step.functional-review.deliverables_met_with_residuals=0
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=2
step.code-review.rules_cited=2
```

## Residue

- Merge commit fff5befe landed through the merge queue on main. The branch was rebased onto c23e2a85 (#898) and force-pushed with lease during branch-cleanup, at the operator's explicit answer to the overlap gate. The recorded branch-cleanup detail still reads "rebase deferred to queue", because record-merge refuses rebase facts on a merge_queue record; the rebase did happen, in the earlier firing of that step. Cost: two full review rounds, gate, CI and triage re-ran on the rebased head.
- Open advisories (not blocking, recorded in the decision log): LogoutTokenValidator.numericDate scales an attacker-sized BigDecimal exponent inside decisionLock (a 10-character iat of 1e99999999 costs tens of seconds and blocks every other logout validation on that validator; fix kind correction, bound precision()-scale() before setScale); the out-of-range numericDate branch has no test; test classes above the 500-line guideline; JwksEndpointHealthCheck has untested branches; ADR-0029 revision-history style; a backward wall-clock step could let a purged jti be replayed (accepted).
- Four poms (benchmark-core, token-sheriff-client, quarkus-integration-tests, validation-quarkus) gained <type>test-jar</type> on the validation generators dependency, a disclosed change outside the plan deliverables that fixes a pre-existing reactor test-compile failure. The underlying plan-marshall build-maven reactor test-canonical defect is NOT fixed (candidate-lesson 001 in this epic's inbox). The token-sheriff-client architecture enriched.json hint about that note is stale and was not updated.
- The integration-tests module verify in the execute leaf hit its learned 302s timeout (4334 passed, 0 failed) and was treated as covered by the whole-tree verify -Ppre-commit (4512 tests green on the final head).
- Some leaf-authored commits carry a Claude co-author trailer in addition to the plan-marshall trailer; not amended.
- The github_pr fetch_findings self-response-loop guard fired on CodeRabbit's in-place-edited summary comment (Q-Gate d803c2) and the operator accepted it as benign.
- 23 candidate-lesson messages from lessons-capture are in this epic's inbox (plan-05-validation-public-seams-001 to -023).
