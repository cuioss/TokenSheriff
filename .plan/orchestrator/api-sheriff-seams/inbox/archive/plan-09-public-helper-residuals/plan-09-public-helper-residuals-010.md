envelope_version=1
sender_type=plan
sender_id=plan-09-public-helper-residuals
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-17T16:32:53Z

# Candidate lesson: CodeRabbit caught hostile-input and test-propagation defects the in-house reviews passed

**Signal source:** automatic-review (signal 2). Review-bot findings remediated in-run (`pr-comment`, resolution `fixed`), PR #916, bot coderabbit.

**Records (fix tasks from the wait-region / finalize-feedback triage):**
- 6bea91 -> TASK-15: `BearerChallenge.Builder.build()` joined and copied a single 1,000,000-character valid scope before its final length check refused it. The scope-count cap does not bound a single scope's length. Fix: refuse an over-long scope text before the join.
- 0a93ff -> TASK-16: `HttpJwksLoaderExecutorHandOffTest`. A failure inside the gated worker thread did not reach the test thread, so the test could pass while the worker assertion failed. The red proof confirmed the fix.
- dde2de nitpick -> TASK-17: the deployment-time refusal was pinned only by the build-step test, not through Quarkus augmentation. Added `BearerScopeValidationRefusalBootTest`. The red proof showed it fails when `@BuildStep` is removed, while the build-step test stayed green.
- 98722e -> TASK-18 (operator decision): the streaming memory-bound wording understated the doubling-array growth peak.
- b97ac6 -> TASK-19: the malformed Content-Length requirement was scoped wider than the handler's own observation of the header.

**Observation:** All five passed the in-house functional-review and code-review rounds before the bot saw them. Two of them are behavioural: allocation before a bound check, and a lost worker-thread assertion. One more is a test that could not fail for the reason it claims to pin. The project already watches for this class: the functional review's `false_self_report` advisory at f527cc6f noted that the boot test does not pin refusals.

**Possible corrective action (for the orchestrator to judge):** Add two review-rubric checks for this epic's helper work. (a) For every length or count bound, check that the bound is applied before any join, copy or allocation sized by the untrusted input. (b) For every test that asserts inside a worker or executor thread, check that the failure is propagated to the test thread, for example by rethrowing from a captured future or reference. Also, for a deployment-time refusal, require a boot or augmentation-level negative test in addition to the build-step unit test.
