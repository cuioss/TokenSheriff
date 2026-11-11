# PLAN-14: Client adversarial test gaps and test hygiene

epic: quality-report
workstream: WS-02

## Objective

The client attack-database tests collapse to "non-JSON is rejected" and never send valid-JSON adversarial
token responses (TC-7, client gap 7); discovery lacks tests for an HTTPS document advertising `http://`
endpoints, a wrong Content-Type and 3xx handling (gap 8); `iss`/logout-URI case and trailing-slash
normalization is untested (gap 9); and two hygiene items remain (TC-9: a second top-level test class in
`FetchPathAttackDatabaseTest.java`, an unused `@EnableGeneratorController`). Mostly tests; a test that
exposes a real defect fixes it in production code within this plan. Closes TC-7, TC-9 (unit part), client gaps 7/8/9.

## Deliverables

1. **TC-7 / gap 7** — valid-JSON adversarial MockWebServer responses: missing `access_token`, wrong-typed
   `expires_in`, duplicate keys, oversize values, unknown `token_type`; assert the exact typed failure.
2. **gap 8** — discovery: HTTPS document advertising `http://` endpoints, wrong Content-Type, 3xx redirect.
3. **gap 9** — `IssValidator` and `EndSessionFlow` case/trailing-slash normalization tests (pin exact-match
   semantics per RFC 9207).
4. **TC-9 hygiene** — move `TokenEndpointTransportControlTest` to its own file; drop the unused
   `@EnableGeneratorController` from `PkceChallengeTest`.

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
- If a new test exposes a defect, the fix stays minimal and is named in the PR as a new finding (not one of the report IDs).

## Claim Labels

- OBSERVED: `FetchPathAttackDatabaseTest` / `RetrievalParsingAttackDatabaseTest` assert only `TransportException` on non-JSON — read at `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/hardening/RetrievalParsingAttackDatabaseTest.java`
- OBSERVED: no test for missing `access_token`, wrong-typed `expires_in`, duplicate keys — swept `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/`
- OBSERVED: `DiscoveryAdversarialTest` has 3 tests (5xx, invalid JSON, issuer mismatch); non-TLS issuer moved to `DiscoveryResolverTest` by #796 — read at `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/discovery/DiscoveryAdversarialTest.java`
- OBSERVED: only `IssValidatorTest` "differs only in trailing length" exists; nothing in `EndSessionFlowTest` — read at `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/IssValidatorTest.java`
- OBSERVED: `TokenEndpointTransportControlTest` is a second top-level class in `FetchPathAttackDatabaseTest.java`; `PkceChallengeTest` has `@EnableGeneratorController` without `Generators` use — read at `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/PkceChallengeTest.java`

## Expected Surface

- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/hardening/RetrievalParsingAttackDatabaseTest.java`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/hardening/FetchPathAttackDatabaseTest.java`
- HYPOTHESIS: new `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/hardening/TokenEndpointTransportControlTest.java` (moved class — verify-at-outline)
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/discovery/DiscoveryAdversarialTest.java`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/IssValidatorTest.java`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/logout/EndSessionFlowTest.java`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/PkceChallengeTest.java`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/TokenEndpointClientTest.java`

## Dependencies and Sequencing

- Depends on: PLAN-06 (`EndSessionFlow` behaviour) and PLAN-10 (`TokenEndpointClient` behaviour)
- Overlaps with: PLAN-06 (`EndSessionFlowTest.java`), PLAN-10 (`TokenEndpointClientTest.java`) — both land first
- Adjacent to: Keycloak ITs — PLAN-16

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/quality-report/plans/PLAN-14-client-adversarial-tests.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
