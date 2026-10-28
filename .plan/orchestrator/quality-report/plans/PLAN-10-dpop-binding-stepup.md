# PLAN-10: Client DPoP issuance binding, step-up clock, PAR wiring decision

epic: quality-report
workstream: WS-02

## Objective

When the client requested a DPoP-bound token, it accepts whatever `token_type` the server returns and
never checks `cnf.jkt` against its own key (C-3). Step-up `auth_time` verification uses `Instant.now()`
with no skew and no injectable clock (C-8d). PAR is produced as a bean but no flow composes a
`request_uri` redirect (C-8c). Closes C-3, C-8c, C-8d.

## Deliverables

1. **C-3** — when the sender constraint is DPoP, `TokenEndpointClient`/`AuthorizationCodeFlow` require
   `token_type=DPoP` and, when the access token is a JWT carrying `cnf.jkt`, that it equals the
   generator's thumbprint; update `ConstraintBinding` Javadoc. Tests for Bearer-downgrade and jkt mismatch.
2. **C-8d** — `StepUpResultVerifier` takes a `Clock`/`InstantSource` and a skew allowance; fixed-clock tests
   at the boundary.
3. **C-8c** — decide at outline: wire PAR into `AuthorizationRequestBuilder` (`request_uri` + `client_id`
   redirect honouring `expires_in`) or document PAR as a building block not used by built-in flows.
   Record the decision in the PR.

## Standing Constraints

- Every claim below was re-grounded at HEAD `aa77acd9`; re-verify each against the implementing
  source at outline before scoping on it (refutation loops back to re-scope). The original report
  text is in `.plan/orchestrator/quality-report/source-report/` (read-only, stale line numbers).
- The PR title/body references every finding ID it closes (e.g. "Fix V-3"). Do NOT edit
  `source-report/` or `findings-disposition.md` — the orchestrator records dispositions.
- Fail-closed posture is non-negotiable: no fix may widen acceptance of a token, a callback or a
  configuration that is rejected today, unless the deliverable says so explicitly.
- Every behavioural fix ships with a test that fails before and passes after (JUnit 5, CUI test
  conventions, generators where variation matters); build and verify via Maven only.
- Log changes follow CUI logging: LogRecord in the module's LogMessages class, and
  `doc/LogMessages.adoc` updated in the same PR for any new/renumbered identifier.
- SonarCloud analyses PRs (`sonar: configured`): an open Sonar issue is fixed in a pushed commit,
  or dismissed with a stated reason recorded as `{issue-key}: {reason}` in the run's progress
  record and transmitted via `plan-marshall:workflow-integration-sonar:sonar_rest transition
  --issue-key {key} --transition {falsepositive|wontfix}`. A dismissal the run cannot transmit is
  a blocker. The merge gate reads the open-issue count at the head — `0` confirmed or every
  remaining issue dismissed; an `undecidable` read blocks.

## Claim Labels

- OBSERVED: `TokenEndpointClient` only checks `hasRecognizedTokenType()` (Bearer or DPoP regardless of request) — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/TokenEndpointClient.java`
- OBSERVED: `AuthorizationCodeFlow` calls `validateAccessToken` with no expected `jkt` — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/AuthorizationCodeFlow.java`
- OBSERVED: `verifyAuthTime` uses `Instant.now()`, no skew; no `Clock`/`InstantSource` in client main — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/StepUpResultVerifier.java` § `verifyAuthTime`
- OBSERVED: no `StepUpResultVerifierTest` exists — swept `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/`
- OBSERVED: `ParClient` is only produced as a bean; no flow composes `request_uri`; `ParResponse.getExpiresIn()` unused — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/ParClient.java`
- HYPOTHESIS: access tokens may be opaque, so the `cnf.jkt` check must be conditional on a parseable JWT — confirm/refute at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/dpop/ConstraintBinding.java` (verify-at-outline)

## Expected Surface

- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/TokenEndpointClient.java`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/AuthorizationCodeFlow.java`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/dpop/ConstraintBinding.java`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/StepUpResultVerifier.java`
- HYPOTHESIS: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/AuthorizationRequestBuilder.java` and `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/ParClient.java` (only if PAR is wired — verify-at-outline)
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/TokenEndpointClientTest.java`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/AuthorizationCodeFlowTest.java`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/ParClientTest.java`
- HYPOTHESIS: new `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/StepUpResultVerifierTest.java` (verify-at-outline)

## Dependencies and Sequencing

- Depends on: none (sequence after PLAN-06 if PAR is wired, because of `AuthorizationRequestBuilder.java`)
- Overlaps with: PLAN-06 (`AuthorizationRequestBuilder.java`, single file); PLAN-14 (`TokenEndpointClientTest.java`) — PLAN-14 after
- Adjacent to: validation-side DPoP (PLAN-09) — independent module

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/quality-report/plans/PLAN-10-dpop-binding-stepup.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
