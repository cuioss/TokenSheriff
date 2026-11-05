# Landing Analysis: PLAN-10 — Client DPoP issuance binding, step-up clock, PAR wiring decision

epic: quality-report
workstream: WS-02
pr: 856 (https://github.com/cuioss/TokenSheriff/pull/856)

> Landing record for one shipped plan. Written by the `analyze` verb (inbox scan, message
> `plan-10-dpop-binding-stepup-010.md`) after verifying the claims against the merged source, the PR state read
> through the CI abstraction, and SonarCloud. A landing message is a lead, not a fact.

## Deliverable Fidelity vs Spec

Corroborated against `origin/main` at the merge commit `02c0fd3a` (read: `git show --stat`, `git grep` in the merged
tree) and the PR (`ci pr view --pr-number 856`: `state: merged`, `merge_commit_sha`
`02c0fd3a9340f1655a91561897543729bd687d87`).

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. C-3 DPoP issuance binding: require `token_type=DPoP`, and for a JWT access token carrying `cnf.jkt` that it equals the generator's thumbprint; `ConstraintBinding` Javadoc; Bearer-downgrade and jkt-mismatch tests | shipped-modified (stricter than specified, at a different seam) | The check lives once in `TokenEndpointClient` (the token-endpoint seam, so the authorization-code, client-credentials and refresh grants all inherit it), not in `AuthorizationCodeFlow` (untouched by the merge). It is fail-closed beyond the spec: a JWT access token with `cnf.jkt` **absent**, a non-object `cnf` or a non-string `jkt` is rejected, not only a mismatching one; an opaque or JWE access token skips only the `jkt` comparison and still needs `token_type=DPoP`. New `internal/AccessTokenConfirmation.java` (81 lines) decodes `cnf`. The `ConstraintBinding` Javadoc and `package-info.java` were updated. The spec hypothesis "access tokens may be opaque, so the `jkt` check must be conditional on a parseable JWT" is confirmed. Tests: `TokenEndpointClientTest` (+357), `AuthorizationCodeFlowTest` (+95), `RefreshFailureClassificationTest` (+57), `DpopBoundAccessTokens` fixture (new, 73). |
| 2. C-8d `StepUpResultVerifier` takes a clock and a skew allowance; boundary tests | shipped-as-specified (plus a fail-closed guard added in review) | `StepUpResultVerifier` takes an `InstantSource` and a `Duration` skew, default `InstantSource.system()` and `Duration.ZERO`; skew only ever widens the `max_age` boundary and a negative skew is refused. New `StepUpResultVerifierTest` (311 lines, fixed-clock boundary tests). Beyond the spec, a CodeRabbit finding (060f53) made a negative server-supplied `max_age` fail closed. |
| 3. C-8c decide at outline: wire PAR or document it as a building block | shipped as the documentation arm | The PR body records the decision: PAR stays a building block the built-in flows do not use. `doc/client/requirements.adoc` now says so (read in the diff: "The engine MUST provide PAR as a building block ... The built-in authorization-code flow does not use PAR"); `ParClient`, `AuthorizationRequestBuilder` and `ParClientTest` are untouched. |

**Added beyond the spec.** Eleven documentation, diagram and README paths (architecture, best-practices,
requirements, retrieval-flow, step-up-and-logout, test-strategy, token-handling, threat-model, the
`client-auth-code-pkce.svg` diagram, the client README), which carry the corrected "PAR is not a step of the built-in
flow" claim and the new issuance-binding rules. Also a fix to a **pre-existing** defect found by a review round:
`TokenEndpointClient.parse()` and `errorCode()` caught only `IOException`, but DSL-JSON throws an unchecked
`ArrayIndexOutOfBoundsException` on a body that ends inside a string escape; the exception escaped after the grant
was redeemed and `RefreshFlow` classified it as a pre-redemption failure (a wrong retry decision for a one-time
grant). The merged helper folds the runtime exception into `IOException` with a documented suppression marker (read
at `TokenEndpointClient.java` lines 449 and 469).

**Surface delta (landing-check, `unmeasured`: the landing supplied neither side).** Computed here from the spec and
the merged diff: declared 11 paths (counting the PAR hypothesis pair and the new test as declared), realized 23, 6
matched, 17 added, 4 missing. Matched: `TokenEndpointClient.java`, `ConstraintBinding.java`,
`StepUpResultVerifier.java`, `TokenEndpointClientTest.java`, `AuthorizationCodeFlowTest.java`,
`StepUpResultVerifierTest.java`. Missing: `AuthorizationCodeFlow.java` (the seam moved to the token endpoint),
`AuthorizationRequestBuilder.java`, `ParClient.java`, `ParClientTest.java` (the PAR documentation arm). Added: the 11
doc paths, `dpop/package-info.java`, `AccessTokenConfirmation.java`, `DpopBoundAccessTokens.java`,
`RefreshFailureClassificationTest.java`, `WiredFlowNegativePathIT.java`, `WiredFlowTestSupport.java`,
`BackChannelOriginRoutingTest.java`. The spec is a record and is not edited.

## Metrics and Anomalies

- Tokens: 23,092,820 (landing message; the plan flags the population as mixed and the 5-execute phase figure as
  over-counted by roughly 125 thousand). Duration 23,793 wall seconds, about 6.6 hours: the longest of the code plans
  (PLAN-11 was 3.2 hours).
- Effort: `unknown` (the runtime stamp could not be read). Not stamped; the landing is incomplete on this key only.
- Anomalies:
  - Finalize looped back three times before the PR existed (a missed target file, a fail-open malformed-`cnf`
    branch, and the DSL-JSON exception), about 90 minutes of re-dispatched reviews (candidate lesson 003).
  - Commit `25e892e` was made inside the `pre-push-quality-gate` step (an OpenRewrite-inserted TODO replaced by the
    suppression marker) and post-dated the functional and code reviews; `310669e` (the two CodeRabbit fixes) then
    forced a full re-fire of the head-dependent chain once (lessons 005, 006).
  - A code-review verifier wrote its verdict under the wrong key and was read as malformed (lesson 007; only partly
    confirmed by the landing itself).

## Routing and Merge Behavior

- Review: the landing's `code-review` reports 0 blocking and 1 advisory, 1 rule cited; functional review 4 of 4
  deliverables met, no residuals, no unevidenced criteria (landing message; not independently verified).
- CI/merge: `ci checks status` on #856 returns `overall_status: success` over 24 checks, `Run JMH Benchmarks`
  included (it finished `SUCCESS` after the plan's post-merge wait gave up). Merged through the merge queue as
  `02c0fd3a` and read back from the PR as `state: merged`. The merge was under a `barrier-ask-override`
  (`review-barrier-gap`, unproven bots coderabbit and sourcery): CodeRabbit was quota-limited on the final commit
  `310669e`, which holds only its own two suggested fixes. The operator accepted merge-anyway at the plan's prompt.
- Sonar: gate `OK` on PR 856 read directly (new coverage 97.9, duplication 0.0, security hotspots reviewed 100, all
  three ratings 1), and `search --pr 856` returns 0 issues. A PR-scoped snapshot, not a re-run.
- Concurrency: PLAN-10 ran beside PLAN-23 and PLAN-11, and PLAN-13 started after it. **One unpredicted overlap:** PLAN-10
  changed `RefreshFailureClassificationTest.java`, which PLAN-23 (still running) declares and PLAN-10 did not. PLAN-23
  rebases over it; recorded as a Watch.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-10 --status shipped`
- [x] row `pr` stamped — value 856
- [x] row `landing` stamped — value `landings/PLAN-10.md`
- [x] row `plan_marshall_plan_id` already `plan-10-dpop-binding-stepup`; it matches the message sender
- [x] `findings-disposition.md`: C-3, C-8c, C-8d marked FIXED
- [x] epic.md: Decisions entry, Queue annotation, Open Defect (incomplete landing, `effort`), Watches
- [x] nine candidate-lesson messages dispositioned (see Follow-Ups)
- [x] resume anchor updated in `resume_anchor.md`; `queue-view.md` regenerated

## Follow-Ups

- Folded into PLAN-22 (the residual-documentation bucket): `doc/client/specification/step-up-and-logout.adoc`
  (lines 59 to 68) and the `StepUpHandler.verifyResult` `@throws` still omit that a negative `max_age` is rejected.
  Verified: the spec text mentions only a negative skew allowance. `StepUpHandler.java` is added to PLAN-22's Expected
  Surface. The commit message of `3f1ab1b` overstating CLIENT-11 coverage cannot be fixed after merge and is dropped.
- Operator decision 2026-11-05 (fold into PLAN-15): the four validation-module sites below are PLAN-15 deliverable 6;
  the three client-side sites stay a Watch. The pre-existing DSL-JSON unchecked-exception class: `git grep` finds eight more
  `dslJson.deserialize` sites that catch only `IOException` (`DiscoveryResolver`, `ParClient`, `UserInfoClient` in the
  client; `JwksHttpContentConverter`, `WellKnownConfigurationConverter`, `JwksParser` (two sites) and
  `NonValidatingJwtParser` in validation). Whether each is reachable with an unchecked exception was NOT tested here;
  the lesson says "worth a sweep".
- PLAN-18 and PLAN-19 declare files PLAN-10 changed (`doc/client/threat-model.adoc`, `requirements.adoc`,
  `retrieval-flow.adoc`, `architecture.adoc`, the client README; PLAN-19 also `token-handling.adoc`,
  `test-strategy.adoc`). Both re-read those at their own outline. PLAN-14 declares `TokenEndpointClientTest.java`
  (changed) and is now unblocked.
- The nine candidate lessons: 001 and 003 to 009 are plan-marshall-owned and went into the 2026-11-05 PLAN-10 addendum
  of `upstream-plan-marshall-report.md`; nothing was sent.
