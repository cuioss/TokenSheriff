envelope_version=1
sender_type=plan
sender_id=plan-09-public-helper-residuals
epic=api-sheriff-seams
kind=landing
created=2026-11-17T16:35:39Z

## What landed

plan-09-public-helper-residuals shipped as #916 (merged, queue merge commit 67f8e480960dad36e3b784cb1feb123301165554).

```landing-facts
schema=landing-facts/1
plan_id=plan-09-public-helper-residuals
epic=api-sheriff-seams
pr=#916
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=33889428
total_wall_seconds=33262.0
harness=claude
model=claude-sonnet-5-5
effort=medium
plugin_version=0.1.2133
executor_version=0.1.2133
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done
unresolved_review_threads=unknown
review_body_findings_total=unknown
review_body_findings_handled=unknown
sonar_open_new_issues=unknown
step.functional-review.deliverables_met=2
step.functional-review.deliverables_met_with_residuals=3
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.code-review.blocking_count=0
step.code-review.advisory_count=13
step.code-review.rules_cited=12
```

## Residue

- **The `branch-cleanup` record understates the path.** Its rendering reads `rebase deferred to queue`. This run did rebase: the pre-rebase gate classified the branch as overlapping with main without a content conflict, the operator chose to rebase and force-push, and the branch moved from 57c29c23 to 0926f3ad (main had gained PLAN-03, #915). The merge was then dispatched through the merge queue; the documented route after that gate verdict (`queue_wait: forbidden`) is the immediate `safe-merge`, so the queue route was a deviation. The queue re-tested the head and the merge-group run concluded success. A second ledger-only commit (#917) landed on main during the run; the classifier read no overlap with it.
- **Operator decisions taken in the run.** Unrepresentable `@BearerAuth` / `@BearerToken` scopes are refused at deployment, with a defined 500 `SERVER_MISCONFIGURATION` and a `TokenSheriff_Q-201` log on the request path as backstop. A failed executor hand-off still burns the unknown-kid window, with a read-only pre-check before the guard. The body handler keeps the single doubling array (wording revised, memory bound stated as below twice the ceiling plus the chunk in flight). The public method `BearerTokenResponseFactory.insufficientScopeChallenge` was accepted (criterion D1.C2 authorized). Two limits of the deployment check were accepted: a method-level annotation on an interface default method is checked on its own, and a concrete-typed override of a generic method is not recognised as an override.
- **Weakened-test row accepted at every head.** The functional and code reviews flagged `JwksEndpointHealthCheckIssuerStatusFailureTest.ConfiguredIssuer::checkConfigured` (two assertions) at every head. It is a counter artefact: the issuer-in-WARN assertion lives in `assertWarnNamesIssuer`, still called by the four tests, and the verb folds the three-line `assertSanitisedFailure` helper into the previous identity. The operator accepted it; the row was re-accepted at each of seven heads.
- **Open advisory findings (not fixed here).** The last code review carries 13: VALIDATION-14.2 wording over-states when the 500 applies; no fixture for the interface-default-method branch; the Q-201 log truncation at 256 characters is unpinned; Javadoc literals 64 and 8192 instead of the constants; no control-character scope-name test; the `BearerChallengeTest` retention bound and the `BoundedBodyHandlerTest` object-count growth are unobserved; the separator arithmetic is unpinned; a test name overclaims an ordering. Four more came with the last docs-only commit (0baf5776) and are all in text this plan wrote: the `HttpJwksLoader` Javadoc keeps the unqualified claim that a miss never makes a periodic cycle skip its fetch, which ADR-0030 and the discovery doc now qualify; the ADR-0030 and discovery-doc passages call the guard-holding miss "the first miss" although it is the one that loses the window; the ADR-0031 Consequences state the body-handler peak without the in-flight chunk; the ADR-0031 limit of the request-time 500 to sets that pass the deployment omits a set supplied programmatically.
- **Review and Sonar numbers.** The four keys above are `unknown` because no finalize step records them as facts; they are not zero. What the run did observe: Sonar reported 0 new-code issues, confirmed at head 0baf5776 (issues only, hotspots not counted); eight CodeRabbit findings were fixed in-run and every inline thread was answered and resolved, the summary and review-body comments were taken into account, and no reply was deferred or untransmitted at the merge. CodeRabbit was quota-limited on the last push; the merge relied on its finished review on record, which the participation predicate accepted. Sourcery (optional) refused on its weekly quota throughout.
- **Post-merge CI wait.** The post-merge `checks wait` on the PR did not conclude inside the Bash ceiling (a bot check was still pending), was stopped, and the merge-group run (44122451872) had already concluded success.
- **Candidate-lesson messages.** `lessons-capture` wrote 12 candidate-lesson messages (001 to 012) and the preference emitter one owed architecture hint (013, module `maven`: frontend build output classified as build errors by the log parser).
