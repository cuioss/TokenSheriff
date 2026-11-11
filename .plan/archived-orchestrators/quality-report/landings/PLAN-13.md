# Landing Analysis: PLAN-13 — Log-record hygiene, response-size ceiling, egress policy

epic: quality-report
workstream: WS-01
pr: 861 (https://github.com/cuioss/TokenSheriff/pull/861)

> Landing record for one shipped plan. Written by the `analyze` verb (inbox scan, message
> `plan-13-log-transport-hygiene-007.md`) after verifying the claims against the merged source, the PR state read
> through the CI abstraction, and SonarCloud. A landing message is a lead, not a fact.

## Deliverable Fidelity vs Spec

Corroborated against `origin/main` at the merge commit `7145aebb` (read: `git show --stat`, and the added and removed
lines of `JWTValidationLogMessages`, `TransportLogMessages`, `EgressPolicy` and `ParserConfig`) and the PR
(`ci pr view --pr-number 861`: `state: merged`, merge commit `7145aebbe77ceb4d2da24607a774675c89a29628`).

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. V-13 template/argument alignment | shipped | `JWKS_LOAD_FAILED` is now `"Failed to load JWKS for issuer %s: %s"` and `JWKS_KEYS_UPDATED` is `"JWKS keys updated for issuer %s, key ids: %s"` (read in the diff). The other call sites were not each re-read. |
| 2. V-14 / D-4 / D-3 duplicate identifier and doc | shipped | `DUPLICATE_JWKS_KID` moved from identifier 168 to 171 (read); new `LogMessageIdentifierUniquenessTest` (+278) guards uniqueness; `doc/LogMessages.adoc` changed (+17/-). |
| 3. V-15 separate HTTP response ceiling | shipped | `ParserConfig` gains `maxHttpResponseSize` with `DEFAULT_MAX_HTTP_RESPONSE_SIZE = 256 * 1024`, independent of `maxPayloadSize` (read); `JwksHttpContentConverter` and `WellKnownConfigurationConverter` changed. |
| 4. V-17a `EgressPolicy` ranges and wording | shipped | The policy now blocks `100.64.0.0/10`, `0.0.0.0/8` (not only `0.0.0.0`) and `255.255.255.255` (read in the Javadoc and the new broadcast test). The "same fresh resolution" wording was not re-read. |
| 5. V-17h `JWKS_KEYS_UPDATED` content | shipped | The record carries issuer and key ids (read). |
| 6. PLAN-21 residual: unusable `jwks_uri` retried forever | shipped | New record `JWKS_URI_UNUSABLE`, identifier 172: "Advertised jwks_uri for issuer %s cannot be used, the loader stops retrying: %s" (read). `HttpJwksLoader` +241 lines with `HttpJwksLoaderFailureTest` +534. |

The landing counts 7 deliverables where the spec lists 6: the run split one at outline (not asked which).

**Surface delta (landing-check, `unmeasured`: the landing supplied neither side).** Computed here from the spec's 14
declared paths (`corpus surfaces`) and the merged diff: declared 14, realized 20, 12 matched, 8 added, 2 missing.
Added: `HttpJwksLoaderFailureTest`, `HttpJwksLoaderSchedulerTest`, `HttpJwksLoaderTest`,
`LogMessageIdentifierUniquenessTest`, `WellKnownResultConverterTest`, and the docs `api-reference.adoc`,
`openid-discovery.adoc`, `security-settings.adoc`. Missing: `WellKnownConfigurationConverterTest` (the new rows went
into `WellKnownResultConverterTest`) and `JWKSKeyLoaderTest` (the identifier renumber needed no edit there; PLAN-15
declares it and re-reads it). The spec is a record and is not edited.

## Metrics and Anomalies

- Tokens: 34,670,813; duration 116,534 wall seconds, about 32.4 hours (landing message; the longest plan so far).
  The figure is a mixed population: execute-phase subagent usage was accumulated into the finalize accumulator and a
  zero-usage dispatch-boundary row was recorded, so the per-phase split is skewed (landing message).
- Effort: `low`, stamped; `model` is self-reported (`refresh-runtime-stamp` reported `model_unknown`). The landing is
  complete (`landing-check`: `complete: true`).
- Anomalies: CodeRabbit's quota refusal forced closing PR #859 and recreating it as #861 to obtain a fresh review. The
  first merge-queue enqueue was ejected when the merge-group `e2e-tests / test` job hung to its 30-minute timeout
  (it passed in 185 s on the PR head and on the second enqueue); one loop-back was spent. The `license/cla` check
  stayed pending 30+ minutes and was overridden with a logged decision. 130 auto-filed `build-error` findings from a
  stand-alone `test-compile` had to be bulk-rejected (the known PLAN-11-002 artefact).

## Routing and Merge Behavior

- Review: functional review 6 met, 1 met with residuals, 0 not met, 0 unevidenced; code review 0 blocking, 9
  advisory, 7 rules cited (landing message). Six candidate lessons sent separately.
- CI/merge: `ci checks status` on #861 returns `overall_status: success`. Merged through the merge queue as
  `7145aebb`, read back as `state: merged`. No barrier override is recorded for this plan.
- Sonar: gate `OK` on PR 861 read directly (new coverage 89.1, duplication 0.0, hotspots reviewed 100, all ratings 1).
  `main` read through the Sonar REST API after the merge analysis (revision `7145aebb`, 2026-11-05T13:11Z): 1 open
  issue, the known `java:S8786` at `JwtTokenTamperingUtil.java:450`, owned by PLAN-15.
- Concurrency: ran beside PLAN-14 and PLAN-22 with no reported collision.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-13 --status shipped`
- [x] row `pr` stamped — value 861
- [x] row `landing` stamped — value `landings/PLAN-13.md`
- [x] row `plan_marshall_plan_id` stamped `plan-13-log-transport-hygiene` (the message sender)
- [x] `findings-disposition.md`: V-13, V-14, V-15, V-17a, V-17h, D-3, D-4 marked FIXED
- [x] `epic.md`: Decisions entry, Queue annotation, Watches; no new Open Defect (the landing is complete)
- [x] six candidate-lesson messages dispositioned (see Follow-Ups); resume anchor and `queue-view.md` updated

## Follow-Ups

- PLAN-15 is unblocked (its dependency on PLAN-13 is met). PLAN-13 changed `WellKnownConfigurationConverter.java`
  and `JwksHttpContentConverter.java`, which PLAN-15 deliverable 6 sweeps: PLAN-15 re-reads them at outline.
- Unsanitised remote text in log records (lesson 006 and the landing's follow-up 1) is FOLDED into PLAN-25
  deliverable 6 after reading the sites at `7145aebb`: `JWKSKeyLoader` line 423 logs the remote `kid`,
  `JwksParser` line 114 logs `e.getMessage()`, and `KeyProcessor` logs the remote `kty`, `kid`, algorithm and
  `e.getMessage()` at about a dozen sites without `ErrorTextSanitizer`.
- The landing's advisory residuals and its follow-up 2 (ADR-0017 amendment: an unusable advertised `jwks_uri` is a
  fourth terminal rejection) are FOLDED into PLAN-25 deliverable 7.
- Candidate lessons 001 to 005 are plan-marshall-owned and went into the 2026-11-05 PLAN-13 addendum of
  `upstream-plan-marshall-report.md` (not sent).
