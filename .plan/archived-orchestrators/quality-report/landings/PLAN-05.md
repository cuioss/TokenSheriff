# Landing Analysis: PLAN-05 — ECDH-ES JWE end to end and uniform RSA-OAEP failure

epic: quality-report
workstream: WS-01
pr: #835 (https://github.com/cuioss/TokenSheriff/pull/835)

> Landing record for one shipped plan. Written by the `analyze` verb (inbox scan) after
> corroborating the landing message `plan-05-jwe-ecdh-es-001.md` against the merged PR and the
> merge commit. Corroborated by this analysis: PR state `merged`, merge commit `e010c5cf`, the PR
> title/body, and the merged file footprint (`git show --name-only e010c5cf`). NOT corroborated:
> code-level behaviour (no build or test was run here) and every figure that comes only from the
> landing message (tokens, wall time, review-step counts, Sonar outcome).

## Deliverable Fidelity vs Spec

Evidence is file-level (the merged footprint and the PR body), not a re-read of the semantics. The
plan's own `deliverables_total=2` counts its internal outline deliverables, not the three spec
deliverables below.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| V-1 `JwtHeader.epk` becomes an object consumed directly by `JweDecryptor` | shipped-as-specified | PR body "Closes V-1"; `JwtHeader.java` types `epk` as the existing `JwkKey` record, `JweDecryptor.java` changed. The spec's fallback (drop ECDH-ES from the default preferences) was not taken: `JweAlgorithmPreferences.java` is untouched |
| gap VAL 8 end-to-end test through the production parser | shipped-as-specified | PR body "gap VAL 8"; `NonValidatingJwtParserJweTest.java`, `TokenValidatorJweTest.java`, `JweDecryptorTest.java`, `JwtHeaderJweTest.java`, `JweTestTokenFactory.java` changed: eight curve and `enc` combinations through `NonValidatingJwtParser` and `TokenValidator` |
| V-17g RSA-OAEP: continue with a random CEK on unwrap failure | shipped-as-specified | PR body "Closes V-17g"; `JweDecryptor.java` changed; bad-unwrap versus bad-tag matrix over every supported `enc`; the CBC tag mismatch now raises the same typed rejection |
| Added, unplanned | added-unplanned | Behaviour: ECDH-ES rejects a non-empty Encrypted Key and an `epk` whose `kty` is not exactly `EC`; a string-encoded `epk` is now rejected (RFC 7518 conformant, fail-closed); every JWE rejection logged and counted once, honouring quiet/opaque decode modes; CWE-117 sanitisation of untrusted header text (`alg`, `enc`, `kid`, `zip`, `crv`, `epk` coordinates); the plain JWS decode message text changed to a fixed message (exception type, event type and cause chain unchanged); sanitised logging in the shared `JwkKey.decodeBase64UrlToBytes` (JWKS and DPoP paths); log record `TokenSheriff-156` retired. Files: `JwkKey.java`, `JWTValidationLogMessages.java`, `doc/LogMessages.adoc`, `token-decryption.adoc`, `ConcatKdfTest.java` (known-answer test for the RFC 7518 Appendix C vector), `WiredNegativePathAttackTest.java`, `UncheckedThrowBoundaryTest.java`, `JwkKeyHandlerTest.java`, `NonValidatingJwtParserTest.java` |
| Declared but not touched | n/a | `JweAlgorithmPreferences.java` |

Surface delta: 17 merged paths against 8 declared. Seven declared paths were touched, among them the
HYPOTHESIS `NonValidatingJwtParser.java` (confirmed). One declared path was not touched
(`JweAlgorithmPreferences.java`), and 10 merged paths were not declared. The PR says no Quarkus module
changed and `JwtHeader.jwk` was left alone, as the spec required.

## Metrics and Anomalies

- Tokens: 36,873,652 (landing message; not independently verifiable here). The total mixes populations (`totals_tokens_population: mixed`) and the pass-7 security-audit sample is over-counted by about 28k.
- Duration: 46,243 s wall (landing message)
- Effort: `unknown` — never stamped, so the landing is incomplete at that key (see Open Defects in `epic.md`).
- Anomalies:
  - Scope grew well past the spec: nine fix tasks added hardening found by review, each round restarting the full gate, audit, review, CI and a rate-limited CodeRabbit cycle. The operator approved continuing at each gate.
  - The pre-push gate's test-compile and module-tests arms were recorded DEGRADED (the gate document assumes a Python build). The real gate was the orchestrator-tier whole-tree `verify -Ppre-commit` (3278 tests). Coverage adequacy was not audited (`required_coverage_unknown`).
  - CI at the final head failed once on an unrelated timing flake in `benchmarking/benchmark-core` (`LibraryMetricsExporterTest`, a 20 ms await) and passed on rerun.

## Routing and Merge Behavior

- Review: the landing carries no `barrier-ask-override` residue, unlike PLAN-03 and PLAN-04, but it does describe a rate-limited CodeRabbit cycle on each fix round. Code review: 0 blocking, 8 advisory (landing message; not independently verified).
- CI/merge: merged through the merge queue as `e010c5cf` (read from the PR: `pr_state: merged`, `mergedAt 2026-10-29T23:25:29Z`).
- Sonar: the landing carries no `step.sonar-roundtrip.*` keys, so no open-issue figure is available for this merge head. Treat as `unknown`, not `0`.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-05 --status shipped`
- [x] row `pr` stamped — `orchestrator queue --set-row PLAN-05 --field pr --value 835`
- [x] row `landing` stamped — `orchestrator queue --set-row PLAN-05 --field landing --value landings/PLAN-05.md`
- [x] row `plan_marshall_plan_id` stamped — value `plan-05-jwe-ecdh-es`
- [x] `findings-disposition.md`: V-1, V-17g and gap VAL 8 → FIXED (per PR, code not re-read)
- [x] epic.md narrative reconciled against the queue rows
- [x] Open Defect opened for the incomplete landing (`effort`); Watches opened
- [x] resume anchor updated in `resume_anchor.md`
- [x] `queue-view.md` regenerated

## Follow-Ups

- `corpus declaration-currency` against the merged footprint: four staged specs declare a path this plan changed. PLAN-09 declares `NonValidatingJwtParser.java` and `NonValidatingJwtParserTest.java` (the planned sequencing, now satisfied); PLAN-17 declares `NonValidatingJwtParser.java`; PLAN-07 and PLAN-13 declare `doc/LogMessages.adoc` and `JWTValidationLogMessages.java`. Each re-verifies its claims at outline. PLAN-07 and PLAN-13 must re-check log-record numbering against the retired `TokenSheriff-156` and any new identifiers.
- PLAN-09 (parser hardening): `NonValidatingJwtParser` now has a fixed JWS decode message and sanitised header logging, so its V-17b trailing-dot work and its tests should assert against the new text.
- Behaviour change to watch in PLAN-15 (tamper tests) and PLAN-14: the plain JWS decode message text changed.
- Not fixed, documented as follow-ups (landing message and PR body, not independently verified): `token-decryption.adoc` line 200 over-generalises the `kty` rejection (a non-string `kty` fails earlier with `FAILED_TO_DECODE_JWT`); the CWE-117 sanitiser is duplicated in `JweDecryptor` and `JwkKey`; a top-level JSON `null` header is rejected but neither logged nor counted (pre-existing); test gaps (null/blank `epk` coordinate branch, `apu`/`apv` decryption, inner-JWS payload decode failure, the unsupported-compression matrix row); two test lines still hold raw U+2028/U+2029 characters. Recorded as a Watch.
- Tooling gaps the landing reports: the installed `pre_merge_gate` has no `record-merge` verb although `branch-cleanup.md` prescribes it; four plan-marshall-side lessons could not be filed because this repo's lesson store rejects plan-marshall components. This plan filed no `candidate-lesson` messages, so they exist only in this record.
