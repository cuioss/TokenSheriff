envelope_version=1
sender_type=plan
sender_id=plan-09-public-helper-residuals
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-17T16:33:06Z

# Candidate lesson: a deployment-time check must reuse the request-path construction it predicts

**Signal source:** Q-Gate findings, 6-finalize code-review. Fixed in-run by TASK-12 (commit 48d239ce), with follow-up limits accepted and backstopped by TASK-13.

**Records:**
- 16142f (TokenSheriffProcessor.java:345, fixed): the deployment scope check left out the realm and error_description that the producer adds. So the 8192-character bound was checked on a shorter challenge than the one actually sent, and a scope set near the bound passed deployment but threw at the first request. Fix: the producer and the deployment check build the challenge through one shared method, `BearerTokenResponseFactory.insufficientScopeChallenge(Collection<String>)`. This needed an operator waiver of the frozen criterion D1 clause "no new public API" (decision log 11:39:51Z).
- 4675f9 (TokenSheriffProcessor.java:322, fixed): the method-level scope union was merged with the declaring class. The interceptor merges it with the bean class, and `BearerAuth` is not `@Inherited`, so check and run time disagreed for inherited methods.
- ec2043 / 187909 (accepted known limits): interface default methods and concrete-typed overrides of generic methods are still mis-merged at deployment time. TASK-13 added a request-path backstop so a miss gets a defined response (500), and documented the limits.

**Observation:** The outline framed the deployment check as reusing `BearerChallenge`'s rule. In practice it re-derived both the challenge text and the annotation-merge semantics separately from the run-time path, and each re-derivation drifted. Both were found only in code review, which cost two finalize loop-backs.

**Possible corrective action (for the orchestrator to judge):** When a build-time or startup check is meant to predict a run-time refusal, the spec should require it to call the same construction seam that runs at request time (cross-module public method if needed, waiver planned up front), and to merge annotations with the same rules the interceptor uses. It should also require a request-path backstop for the cases static analysis cannot represent.
