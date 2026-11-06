# Landing Analysis: PLAN-16 — Keycloak integration tests assert real outcomes

epic: quality-report
workstream: WS-03
pr: 868 (https://github.com/cuioss/TokenSheriff/pull/868)

> Landing record for one shipped plan. Written by the `analyze` verb (inbox scan, messages
> `plan-16-keycloak-it-assertions-001` to `-005`) after verifying the claims against the merged source, the PR state
> read through the CI abstraction, and SonarCloud. A landing message is a lead, not a fact.

## Deliverable Fidelity vs Spec

Corroborated against `origin/main` at the merge commit `306e6053` (read: `git show --stat`, the `TokenEndpointClient`
and `CredentialRejectedException` diffs, the parked ADR draft) and the PR (`ci pr view --pr-number 868`:
`state: merged`). 12 files, +679 / -100.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. TC-3 logout IT, exact `Location`, negative case | shipped | `LogoutSpecIT` +123: the positive case compares the complete `Location` with the echoed `state`, the unregistered URI asserts the rejection response itself (message 002; test bodies not read). `integration-realm.json` +21 registers the URIs. |
| 2. TC-4 mix-up IT through the wired `CallbackHandler` | shipped | `MixUpSpecIT` +147; the honest control now requires `CredentialRejectedException` instead of `RuntimeException` (message 002). Not read line by line. |
| 3. TC-8 client-authentication negatives | shipped, with a production change | `ClientAuthSpecIT` +93. The spec said a wrong secret answers `invalid_client`; Keycloak answers 401 `unauthorized_client`. See below. |
| 4. TC-1 residual: `RefreshErrorPathSpecIT` asserts `invalid_grant` | shipped | `RefreshErrorPathSpecIT` +14. Not read line by line. |
| 5. IT gap 10: PKCE code injection at the AS | shipped | `AuthCodePkceSpecIT` +111; the mismatched verifier now asserts `invalid_grant` and the absence of an `access_token` (message 002). `FullFlowE2EIT` (+13) was tightened too, as the spec allowed. |

**Behaviour change beyond the spec, read in the diff.** `TokenEndpointClient` gained `clientAuthenticationFailed`: a 4xx
whose RFC 6749 §5.2 `error` is exactly `invalid_client`, or exactly HTTP 401 with `unauthorized_client`, raises a new
`ClientAuthenticationFailedException` (79 lines, a `TransportException` subtype, pre-redemption). It is evaluated before
`credentialRejected`; the two allow-lists share no code, so neither shadows the other. The exception message is the
status plus fixed text (no AS-controlled value, CWE-117). `unauthorized_client` on any other status and every 5xx stay a
plain `TransportException`. Tests: `TokenEndpointClientTest` +91, `RefreshFailureClassificationTest` +18 (not read).
Fail-closed: no acceptance widened; the operator chose to normalise the Keycloak answer in the library. The spec was
labelled test-only; its wrong wire value sat in a deliverable with no claim label, which is the orchestrator's authoring
lapse, recorded in `epic.md` Decisions.

**Surface delta (landing-check).** Declared 5, realized 12 (7 added: `ClientAuthenticationFailedException`,
`CredentialRejectedException`, `TokenEndpointClient`, `TokenEndpointClientTest`, `RefreshFailureClassificationTest`,
`integration-realm.json`, `FullFlowE2EIT`). The spec is a record.

## Metrics and Anomalies

- Tokens: 20,285,892; duration 53,212 wall seconds, about 14.8 hours (landing message).
- Effort: `unknown`; `refresh-runtime-stamp` returned `model_unknown`. `landing-check` `complete: false`, missing
  `effort`. Tenth producer: recorded as an Open Defect, not a fault of the landing.
- Anomalies: `create-pr` recorded #866, closed after two CodeRabbit quota refusals and replaced by #868 (the merged
  PR). The plan set `review_rate_window_await=true` for itself and waited about 55 minutes. The pre-push gate reported
  module tests DEGRADED and some builds carried `required_coverage_unknown`; the PR gate below is the stronger evidence.
  The post-merge JMH job was still pending when the wait timed out; it reads `SUCCESS` now.

## Routing and Merge Behavior

- Review: functional review 4 deliverables met, 1 met with residuals, 0 not met; code review 0 blocking, 1 advisory
  (unnamed in the message). CodeRabbit nitpicks: four fixed in `589e08a`, two accepted with a reply.
- CI/merge: `ci checks status` on #868 returns `overall_status: success`, 24 checks. Merged through the merge queue as
  `306e6053`, read back as `state: merged`.
- Sonar: gate `OK` on PR 868 read directly (new coverage 100.0, duplication 0.0, hotspots reviewed 100, all ratings 1).
  `main` after the merge: 1 open issue, the known `java:S8786`, owned by PLAN-15.
- Concurrency: ran beside PLAN-15 (still open as #867); no collision reported.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-16 --status shipped`
- [x] row `pr` stamped — value 868 (not 866, the closed predecessor)
- [x] row `landing` stamped — value `landings/PLAN-16.md`
- [x] row `plan_marshall_plan_id` stamped `plan-16-keycloak-it-assertions` (the message sender)
- [x] `findings-disposition.md`: TC-1 residual, TC-3, TC-4, TC-8, IT gap 10 marked FIXED
- [x] `epic.md`: Decisions entry, Queue annotation, Open Defect (`effort`), Watches
- [x] four candidate-lesson messages dispositioned (see Follow-Ups); all five messages archived
- [x] parked ADR draft preserved at `drafts/adr-draft-failed-client-authentication.adoc` (the original sits untracked in
  the main checkout's `.plan/temp/`, where a cleanup could remove it)

## Follow-Ups

- **ADR draft (message 005).** The plan parked its ADR (typed failed-client-authentication refusal) to save a finalize
  cycle; its number 0020 is taken (the tree holds 0001 to 0026). FOLDED into PLAN-25 deliverable 7(h): file it under the
  next free number, re-checked against `main` at finalize, add the missing client specification and threat-model
  sentence (no file under `doc/client` names `invalid_client` or `unauthorized_client`), and index it.
- **Lesson 001** (spec named the wrong IdP error code; a test-only plan grew a production change): plan-marshall-owned
  (spec authoring and re-grounding in `plan-orchestrator`); upstream addendum, not sent. Local effect: specs that name an
  external wire value label it HYPOTHESIS. The project-level fact (Keycloak answers 401 `unauthorized_client` for a wrong
  secret) is captured by the ADR above.
- **Lesson 002** (negative ITs must assert the server's specific rejection): plan-marshall-owned (test-authoring
  standards); upstream addendum. Project sweep: none needed, a grep of the IT sources for the weak shapes is empty at
  `306e6053`.
- **Lesson 003** (a post-review nitpick commit re-runs every head-bound finalize gate, about 1.5 hours): plan-marshall-owned
  (finalize re-run policy, review triage); upstream addendum.
- **Lesson 004** (an ADR number fixed at draft time collided with ADRs merged meanwhile): plan-marshall-owned
  (`manage-adr` numbering, finalize base-sync); upstream addendum. It happened here: the draft collided with #863.
