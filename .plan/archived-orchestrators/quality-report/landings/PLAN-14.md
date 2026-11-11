# Landing Analysis: PLAN-14 — Client adversarial test gaps and test hygiene

epic: quality-report
workstream: WS-02
pr: 862 (https://github.com/cuioss/TokenSheriff/pull/862)

> Landing record for one shipped plan. Written by the `analyze` verb (inbox scan, message
> `plan-14-client-adversarial-tests-005.md`) after verifying the claims against the merged source, the PR state read
> through the CI abstraction, and SonarCloud. A landing message is a lead, not a fact.

## Deliverable Fidelity vs Spec

Corroborated against `origin/main` at the merge commit `f29ca9cb` (read: `git show --stat`, the `DiscoveryResolver`
and `TokenEndpointClient` diffs, and the moved test file) and the PR (`ci pr view --pr-number 862`: `state: merged`,
merge commit `f29ca9cb03a53be065fbeed51f00b310ed8ee3aa`).

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. TC-7 / gap 7 valid-JSON adversarial responses | shipped, with a production fix | New `TokenResponseAdversarialTest` (+213) and `TokenEndpointClient` (+104): a 2xx body that repeats a top-level member is a `RedeemedResponseException`, a 4xx body repeating a member is treated as an unknown error code (read; the member name is sanitised before logging). The fix is not in the spec. |
| 2. gap 8 discovery: https document advertising `http://`, wrong Content-Type, 3xx | shipped, with a production fix | `DiscoveryAdversarialTest` +258 and `DiscoveryResolver` +80: an `https` issuer whose document advertises a non-`https` value for any of the eight modelled endpoints is refused, and `allowInsecureHttp` does not relax it; every `Content-Type` line must be `application/json` (read in the diff). A 302 on discovery is pinned as observed. |
| 3. gap 9 `IssValidator` and `EndSessionFlow` normalisation | shipped | `IssValidatorTest` +25, `EndSessionFlowTest` +49. Tests only; not read line by line. |
| 4. TC-9 hygiene | shipped | `TokenEndpointTransportControlTest` is a pure move out of `FetchPathAttackDatabaseTest`: the removed and added classes carry the same two methods (`shouldRefuseNonTlsTokenEndpoint`, `shouldReplaceNotDuplicateHeader`) and the same display names (read). `PkceChallengeTest` drops an unused import and `@EnableGeneratorController`. |

**Behaviour changes beyond the spec's text.** Two production narrowings (above), both fail-closed. A discovery
response served as `application/jrd+json` or with no `Content-Type` now fails with `DISCOVERY_FAILED`; that can reject
a provider that served discovery as a different JSON media type before. Docs changed for it (`architecture.adoc`,
`requirements.adoc`, `threat-model.adoc`, the client README, `quarkus-config-doc.adoc`; the `ClientConfiguration` and
`ClientRuntimeConfig` edits are Javadoc only, no new key).

**Surface delta (landing-check, `unmeasured`).** Computed here: declared 8, realized 16, 6 matched, 10 added, 2
missing. Added: `ClientConfiguration`, `ClientRuntimeConfig`, `DiscoveryResolver`, `TokenEndpointClient`,
`TokenResponseAdversarialTest`, and the docs named above. Missing: `TokenEndpointClientTest` and
`RetrievalParsingAttackDatabaseTest` (the new rows went into `TokenResponseAdversarialTest`). The spec is a record.

## Metrics and Anomalies

- Tokens: 21,387,978; duration 23,418 wall seconds, about 6.5 hours (landing message).
- Effort: `unknown` (never stamped). `landing-check` reports `complete: false`, missing key `effort`: recorded as an
  Open Defect, not a fault of the landing.
- Anomalies: the `weakened_test` detector (F7) fired repeatedly on the TC-9 move; I read the moved file and it is a
  false positive. CodeRabbit never produced a review (rate limit); merged under the operator's `barrier-ask-override`
  at head `15eb888a`, each override bound to a specific head. `archive-plan` was still `pending` in the landing's step
  list.

## Routing and Merge Behavior

- Review: functional and code review done; Sourcery reviewed; CodeRabbit (the required bot) did not.
- CI/merge: `ci checks status` on #862 returns `overall_status: success`. Merged through the merge queue as
  `f29ca9cb`, read back as `state: merged`. The post-merge JMH benchmark was still running when the plan's wait ended.
- Sonar: gate `OK` on PR 862 read directly (new coverage 94.2, duplication 0.0, hotspots reviewed 100, all ratings 1).
  `main` at revision `f29ca9cb` and again at `7145aebb`: 1 open issue, the known `S8786`, owned by PLAN-15.
- Concurrency: ran beside PLAN-13 and PLAN-22; no collision reported.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-14 --status shipped`
- [x] row `pr` stamped — value 862
- [x] row `landing` stamped — value `landings/PLAN-14.md`
- [x] row `plan_marshall_plan_id` stamped `plan-14-client-adversarial-tests` (the message sender)
- [x] `findings-disposition.md`: TC-7 FIXED; TC-9 unit hygiene FIXED (the IT trust-all part stays a Watch)
- [x] `epic.md`: Decisions entry, Queue annotation, Open Defect (`effort`), Watches
- [x] four candidate-lesson messages dispositioned (see Follow-Ups); resume anchor and `queue-view.md` updated

## Follow-Ups

- PLAN-25 declares `DiscoveryResolver.java` and its tests, and PLAN-14 changed that file; PLAN-25 re-reads it at
  outline (its deliverable 2 adds the truncated-escape case for the same decode block).
- Three open advisory gaps the landing left "for a dedicated decision" (a blank optional endpoint in an `https`
  discovery document is refused; a quoted numeric `expires_in` is accepted and pinned as observed; the discovery
  ADR candidate was not filed) are FOLDED into PLAN-25 deliverable 7. I also read the other response sites: only
  `DiscoveryResolver` checks the response `Content-Type`; `TokenEndpointClient`, `ParClient`, `RevocationClient` and
  `UserInfoClient` define a `Content-Type` constant for the request only. That is included in the same item.
- Candidate lessons 001 to 003 are plan-marshall-owned and went into the 2026-11-05 PLAN-14 addendum of
  `upstream-plan-marshall-report.md` (not sent). Lesson 004 (enumerate header line-break variants up front) is
  project-owned and FOLDED into PLAN-25 deliverable 7.
