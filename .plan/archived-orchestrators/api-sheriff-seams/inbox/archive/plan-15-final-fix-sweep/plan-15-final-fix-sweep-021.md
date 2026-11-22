envelope_version=1
sender_type=plan
sender_id=plan-15-final-fix-sweep
epic=api-sheriff-seams
kind=landing
created=2026-11-21T12:48:50Z

## What landed

plan-15-final-fix-sweep shipped as #940 (merged, through the merge queue as a433e99005e56f19e5de045c5f6b99887193825c; cleanup_owed false).

```landing-facts
schema=landing-facts/1
plan_id=plan-15-final-fix-sweep
epic=api-sheriff-seams
pr=#940
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=38334593
total_wall_seconds=68899.0
harness=claude
model=claude-sonnet-5-5
effort=medium
plugin_version=0.1.2133
executor_version=0.1.2133
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:running,archive-plan:pending
unresolved_review_threads=unknown
review_body_findings_total=unknown
review_body_findings_handled=unknown
sonar_open_new_issues=unknown
step.create-pr.pr_number=940
step.branch-cleanup.merge_mechanism=merge_queue
step.branch-cleanup.merge_state=merged
step.branch-cleanup.cleanup_owed=false
step.functional-review.deliverables_met=1
step.functional-review.deliverables_met_with_residuals=4
step.functional-review.deliverables_not_met=0
step.functional-review.criteria_unevidenced=0
step.functional-review.blocking_count=0
step.functional-review.advisory_count=21
step.functional-review.head_at_completion=4e1fef614ce04aff1563311008b3c44ee446d6a1
step.code-review.blocking_count=0
step.code-review.advisory_count=7
step.code-review.rules_cited=7
step.code-review.head_at_completion=4e1fef614ce04aff1563311008b3c44ee446d6a1
step.sonar-roundtrip.count_status=confirmed
step.sonar-roundtrip.new_code_issue_count=0
step.record-metrics.total_tokens=38334593
step.record-metrics.total_wall_seconds=68899.0
step.finalize-step-sync-baseline.action=noop
```

## Residue

Notes on the fact block. `steps` shows `emit-landing:running` and `archive-plan:pending` because this message is written while emit-landing is running, before archive-plan runs. The three review numbers and `sonar_open_new_issues` are `unknown` because no finalize step in this lifecycle records them as facts. The landing-payload spec names this producer gap. `sonar-roundtrip` recorded `new_code_issue_count=0` (confirmed), but that count leaves out security hotspots, so it is not the required key. The merge commit SHA is not in the block, because the branch-cleanup step record carries only `merge_mechanism`, `merge_state`, `cleanup_owed` and `work_performed`. The SHA in the headline and in R1 comes from the dispatcher's record of the queue merge.

### R1 Outcome: merged through the merge queue on the second attempt

PR #940 (cuioss/TokenSheriff) merged through the merge queue as a433e99005e56f19e5de045c5f6b99887193825c. It was a queue merge, not a direct merge: merge_state merged, cleanup_owed false. The queue ejected the first attempt because the JDK 27 leg failed on BackChannelHttpBodyPhaseTest, a PLAN-14 probe the spec said to leave alone. The operator chose "Fix the probe first". That change to the spec's constraint is recorded through `manage-solution-outline criteria-authorize` against D2 criterion C3 (decision-log entries e0a7fb and 10d919). The second queue attempt succeeded.

### R2 Verdict per deliverable

- D1: met with residuals.
- D2: met with residuals. The plan departed from C3 by the operator decision authorized in R1. D2.R2 is partly closed.
- D3: met with residuals.
- D4: met.
- D5: met with residuals.

Blocking findings: 0. The functional review closed with 21 advisories and the code review with 7, both at the final head 4e1fef614ce04aff1563311008b3c44ee446d6a1.

### R3 D3 rename record (the rename record the spec asked for)

- Each file was renamed in its own commit, and those commits contain only the renames. The mutation set has 49 files.
- Two classes were moved with git mv: CachingDiscoveryResolverRefreshRegressionTest became CachingDiscoveryResolverRefreshPathTest, and HttpJwksLoaderDefaultBehaviourUnchangedTest became HttpJwksLoaderDefaultBehaviourTest. Their rows in doc/developer/test-impl-mapping.adoc moved in the same commit.
- Each of the 465 test files that carry assertions has the same assertion count before and after. The two moved classes went 43->43 and 23->23.
- 5708 tests ran before the renames and 5708 after.
- Neither former class name appears anywhere in the repository.
- The count tables are in the plan's work/d3-assertion-counts-before.toon and work/d3-assertion-counts-after.toon, and the record is work/d3-rename-record.md.
- The test-integrity check reported 19 weakened_test rows, and the operator accepted all 19: 17 required renames, one deliberate inversion of the checked-cause test, and one merge of a runtime-failure test into the parameterised test.
- One framing hit came later: the DisplayName "should still close the served connection ..." (a state description), added by the review fix commit be9a2550.

### R4 D5 verdict: a defect of the branch build, with an environmental part in the lane's run

start-dev-environment.sh was run once (Docker 27.3.1, Java 25.0.3). A foreign process held 127.0.0.1:8080, so dev mode never became ready in that run. That part was environmental.

The log showed TokenSheriff-141 "Unable to find reader for provided type ...Jwks" and TokenSheriff-203. The cause: ParserConfig.createDslJson used includeServiceLoader(), which uses the thread context class loader. The JWKS load runs on a ForkJoinPool common-pool worker, and in dev mode that worker's loader cannot see the validation jar.

- Fix: includeServiceLoader(ParserConfig.class.getClassLoader()).
- The new JwksParserConfigBootTest fails without the fix and passes with it. Its commit comes before the fix commit.
- pom.xml is unchanged.
- The dev-mode logs are kept in the plan's work/d5-dev-mode.

### R5 NOT DONE: the Playwright ten-run requirement

The ten consecutive runs of accessibility-wcag-compliance.spec.js and self-devui-accessible.spec.js were NOT run. Two preconditions were missing:

- The machine has no Linux npm, only a Windows npm that refuses a UNC working directory.
- The dev environment could not come up because a foreign process held port 8080.

The 2026-11-15 flake entry stays OPEN. This is recorded on purpose, not skipped silently.

### R6 Still open, advisory

- ClientConfiguration.java:186-187 and TokenEndpointClient.java:617-619 still name only the request timeout for the JDK 27 stalled body. The probe now accepts two outcomes.
- RawHttpServerTest: the isConnected assertions are vacuous, and the stall() and injected-socket tests leak when the accept wait fails.
- BackChannelHttpExchangeTest: the checked-cause test is duplicated, and its class Javadoc at :62-66 needs the same attention.
- JwtTokenTamperingUtilReproductionTest: the class name is framed, and it carries the [44] reference.
- JwksParserConfigBootTest: its Javadoc describes the lookup the fix replaced.

### R7 Reviewer posture

CodeRabbit (required) was the only bot that reviewed. Sourcery (optional) refused throughout because its 7-day quota was used up. CodeRabbit raised four comments, and all four were handled: two fixed inline in be9a2550, two informational.

### R8 Process facts for the epic

- The squash message of the queue merge could not be set explicitly. The merge queue is managed outside this repository, configured SQUASH, with no per-enqueue message override. The repository default was NOT changed, because nobody authorized a change to repository settings. The landed commit therefore carries the repository's own squash default.
- The installed marshal.json is stale against the plugin config. The preflight reported it as an advisory (`marshal_status: stale`). The operator should run /marshall-steward.
- lessons-capture and the preference emitter wrote 20 candidate-lesson messages (-001..-020) to this epic's inbox. The lessons-capture step record says 18, and the preference emitter promoted 2 patterns.
- Metrics: record-metrics recorded 19h8m wall time (68899.0 s) and 38.3M tokens (38334593).
