# PLAN-06: Callback and logout hardening

epic: quality-report
workstream: WS-02

## Objective

Harden the client's callback and logout surfaces: check `state` before acting on an error callback
(C-6), sanitize attacker-controlled fields in `CallbackParameters.toString()` (C-5) and Unicode line
separators in the shared sanitizers (C-8a), enforce the endpoint scheme and correct fragment handling
in `EndSessionFlow` (C-4), and decide + pin the single-use contract of `FlowContext` (C-7 / TC-5).
Closes C-4, C-5, C-6, C-7, C-8a, TC-5.

## Deliverables

1. **C-6** — `CallbackHandler.handle()` verifies `state` before throwing on `hasError()`; test: error
   callback with wrong state is refused as a state mismatch.
2. **C-5 / C-8a** — `CallbackParameters.toString()` routes `error`, `errorDescription`, `issuer` through
   `LogSanitizer.sanitize`; `LogSanitizer` and validation's `InboundErrorNormalizer` also neutralise
   U+0085, U+2028, U+2029. Tests in both modules.
3. **C-4** — extract the endpoint-scheme check from `AuthorizationRequestBuilder` into a shared helper;
   `EndSessionFlow.buildLogoutRedirect` uses it and builds the query correctly when the endpoint carries
   a fragment. Tests incl. `http://` endpoint and fragment case.
4. **C-7 / TC-5** — decide at outline: enforce single use (consumed flag on `FlowContext` checked in
   `CallbackHandler`) or keep the documented caller contract. Either way, a test pins the behaviour of
   a second `handle()` with the same context; recommended: enforce.

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

- OBSERVED: `hasError()` throw precedes `statesMatch` — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/CallbackHandler.java` § `handle`
- OBSERVED: `toString()` concatenates `error`, `errorDescription`, `issuer` unsanitized — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/CallbackParameters.java` § `toString`
- OBSERVED: `LogSanitizer.sanitize` and `InboundErrorNormalizer` neutralise only `<0x20` and `0x7F` — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/internal/LogSanitizer.java` § `sanitize`
- OBSERVED: `buildLogoutRedirect` appends with `indexOf('?')`, no scheme check; `enforceEndpointScheme` is private to `AuthorizationRequestBuilder` — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/logout/EndSessionFlow.java`
- OBSERVED: `FlowContext` Javadoc "One-time-use discard contract (L7)": no consumed flag; client-quarkus has no FlowContext store — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/FlowContext.java`
- OBSERVED: no test presents the same `FlowContext` twice (`CodeInjectionTest` covers cross-context only) — swept `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/`

## Expected Surface

- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/CallbackHandler.java`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/CallbackParameters.java`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/FlowContext.java`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/internal/LogSanitizer.java`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/logout/EndSessionFlow.java`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/AuthorizationRequestBuilder.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/error/InboundErrorNormalizer.java`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/CallbackHandlerTest.java`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/CallbackParametersTest.java`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/internal/LogSanitizerTest.java`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/logout/EndSessionFlowTest.java`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/logout/LogoutOpenRedirectTest.java`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/commons/error/InboundErrorNormalizerTest.java`

## Dependencies and Sequencing

- Depends on: none
- Overlaps with: PLAN-10 (`AuthorizationRequestBuilder.java`, single file); PLAN-14 (`EndSessionFlowTest.java`) — PLAN-14 runs after
- Adjacent to: `IssValidator` normalization (client gap 9) — PLAN-14

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/quality-report/plans/PLAN-06-callback-logout-hardening.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
