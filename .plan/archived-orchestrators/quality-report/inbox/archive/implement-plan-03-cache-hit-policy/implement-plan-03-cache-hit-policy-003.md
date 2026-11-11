envelope_version=1
sender_type=plan
sender_id=implement-plan-03-cache-hit-policy
epic=quality-report
kind=candidate-lesson
created=2026-10-29T13:11:22Z

## Candidate lesson: a pre-fix acceptance criterion needs a failing pre-fix run; for a signature-changing fix that proof is a compile failure

**Source**: review rubric criterion D1.C6 (regression tests must fail before the fix), as reported by the dispatcher. Corroborated in the plan's decision log by the phase-5 planned-failure exception for TASK-1 (decision 602ae2): "test compilation is expected to fail on the old AccessTokenCache constructor/put signatures".

**What happened**: D1.C6 was at first not backed by evidence. Nobody had run the new tests against the pre-fix code. It was closed by overlaying the new tests onto the base commit (fb6e2d32) in a scratch worktree and showing that they did not compile. The fix changed the `AccessTokenCache` constructor and `put` signatures and stored per-issuer time policy on `CachedToken`, so the new tests cannot run against the old code at all. The pre-fix proof is a compile failure, not a red runtime test.

**Corrective rule (proposed)**:
- When a plan has a "fails before the fix" acceptance criterion, collect the evidence during execute (overlay the tests on the base commit and record the result), not during review.
- When the fix changes a public signature, expect and record a compile failure as the pre-fix evidence. If a runtime red/green demonstration is wanted, the test must go through an API that is stable across the fix (here, the pipeline-level `TokenValidator` entry point), so the old code compiles and fails at runtime.

**Scope**: plan-marshall execute and review workflows (regression-test evidence), plus the test-authoring choice of entry point for bug-fix regression tests.
