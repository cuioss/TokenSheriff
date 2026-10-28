# PLAN-16: Keycloak integration tests assert real outcomes

epic: quality-report
workstream: WS-03

## Objective

Several Keycloak-backed client ITs can pass for the wrong reason: logout accepts "200 or 302", which a
Keycloak error page satisfies (TC-3); the mix-up IT feeds hand-built callback parameters into the unit
`IssValidator` instead of driving the wired `CallbackHandler` (TC-4); client authentication is tested
positive-only (TC-8); the revocation path does not assert `invalid_grant` (TC-1 residual); PKCE code
injection at the AS is untested (IT gap 10). Closes TC-1 (residual), TC-3, TC-4, TC-8, IT gap 10.

## Deliverables

1. **TC-3** — logout IT with `followRedirects(NEVER)` asserts a 302 whose `Location` equals the registered
   URI plus echoed `state`; negative case with an unregistered `post_logout_redirect_uri`.
2. **TC-4** — mix-up IT drives `CallbackHandler` with a realm-A `FlowContext` and a realm-B `iss`;
   asserts refusal before the token endpoint is called.
3. **TC-8** — `ClientAuthSpecIT` negative cases: wrong secret, absent auth → 401/400 `invalid_client`.
4. **TC-1 residual** — `RefreshErrorPathSpecIT` asserts the `invalid_grant` error code (pattern from
   `RefreshSingleUseSpecIT`).
5. **IT gap 10** — `AuthCodePkceSpecIT`: authorization code issued for verifier A redeemed with a
   different session's verifier is rejected by the AS.

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
- ITs run under the integration-test Maven profile (Keycloak container); verify with that profile,
  and expect the long CI cycle. The trust-all TLS setup of the IT layer (TC-9 IT part) is out of scope.

## Claim Labels

- OBSERVED: `LogoutSpecIT.shouldPerformRpInitiatedLogout` checks `200 || 302`; no negative logout IT — read at `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/test/java/de/cuioss/sheriff/token/integration/client/LogoutSpecIT.java`
- OBSERVED: `MixUpSpecIT.shouldEnforceIssuerBinding` builds `CallbackParameters` by hand into `IssValidator` — read at `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/test/java/de/cuioss/sheriff/token/integration/client/MixUpSpecIT.java`
- OBSERVED: `ClientAuthSpecIT` has exactly two positive tests — read at `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/test/java/de/cuioss/sheriff/token/integration/client/ClientAuthSpecIT.java`
- OBSERVED: `RefreshErrorPathSpecIT#shouldSurfaceRevokedRefreshTokenAsTypedFailure` asserts `TransportException` with "400" but not `invalid_grant` — read at `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/test/java/de/cuioss/sheriff/token/integration/client/RefreshErrorPathSpecIT.java`
- OBSERVED: `AuthCodePkceSpecIT` has only the exchange and mismatched-verifier tests — read at `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/test/java/de/cuioss/sheriff/token/integration/client/AuthCodePkceSpecIT.java`
- HYPOTHESIS: the Keycloak realm fixture registers a fixed `post_logout_redirect_uri` usable for an exact `Location` assertion — confirm/refute at `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/main/docker/keycloak/client-engine-realm.json` § client `post.logout.redirect.uris` attribute (verify-at-outline)

## Expected Surface

- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/test/java/de/cuioss/sheriff/token/integration/client/LogoutSpecIT.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/test/java/de/cuioss/sheriff/token/integration/client/MixUpSpecIT.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/test/java/de/cuioss/sheriff/token/integration/client/ClientAuthSpecIT.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/test/java/de/cuioss/sheriff/token/integration/client/RefreshErrorPathSpecIT.java`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/test/java/de/cuioss/sheriff/token/integration/client/AuthCodePkceSpecIT.java`

## Dependencies and Sequencing

- Depends on: PLAN-06 (callback ordering C-6 affects TC-4 expectations)
- Overlaps with: none
- Adjacent to: `FullFlowE2EIT` (same 200||302 predicate) — tighten too if trivial, else leave

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/quality-report/plans/PLAN-16-keycloak-it-assertions.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
