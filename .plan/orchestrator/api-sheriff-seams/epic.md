# Epic: API Sheriff separation seams (0.9.10)

slug: api-sheriff-seams

> Hand-written narrative for one epic under `.plan/orchestrator/api-sheriff-seams/`. The layout and
> authority contract live in the central standard — see
> `persona-plan-orchestrator/standards/orchestration-model.md`. The ledger JSON files
> (`status.json`, the `queue/{PLAN-ID}.json` rows) and `resume_anchor.md` are the machine
> authority; any statement here that conflicts with them is stale prose.
>
> START HERE and the Ordered Queue are not in this file. They live in the generated,
> git-tracked `queue-view.md` next to it (see the Persist / Stop-Resume Contract in that
> standard). `queue-view.md` is never hand-edited. A merge conflict in it is never merged by
> hand: merge the source files, run
> `python3 .plan/execute-script.py plan-marshall:plan-orchestrator:orchestrator regenerate-view --slug api-sheriff-seams`
> on the merged tree, and `git add` the result.

## Vision

API Sheriff (the gateway, `/home/oliver/git/API-Sheriff`) must not duplicate functionality that
TokenSheriff provides, and generic functionality that lives in API Sheriff but belongs in a token
library must move here. A first duplicate (`BoundTokenEndpointClient`, 354 lines) was removed when
API Sheriff adopted 0.9.9. A follow-up audit (2026-11-12, four read-only reads of API Sheriff main
`26e34cd4` against this repository at 0.9.9 plus the release commit) found that the remaining
overlap is mostly a **missing or private TokenSheriff seam**: the code cannot be deleted on the
API Sheriff side until TokenSheriff publishes the seam. This epic ships those seams in **0.9.10**.
It is too large for one plan because it spans three modules (client, validation, the Quarkus
extensions) and mixes a security-sensitive change (refresh-result verification, the JWKS flood
bound) with small hygiene work. "Done" is: every seam below is released in 0.9.10 (or explicitly
declared out of scope by the operator), and API Sheriff's own plan PLAN-34 in epic
`kidicap-gateway-requirements` can delete the corresponding code against the released version.

**The consumer side is not in this epic.** API Sheriff deletes its copies in its own plan, after a
released 0.9.10 is pinned (the release workflow opens the bump PR). This epic only adds
TokenSheriff API. Everything new is additive and opt-in unless a spec states otherwise; the
fail-closed posture is never loosened.

## Queue annotations

- PLAN-01 — smallest and safest; run first. Closes a fail-open trap in native image for every
  TokenSheriff consumer, not only API Sheriff.
- PLAN-02, PLAN-03, PLAN-04 — all in `token-sheriff-client`; PLAN-02 and PLAN-04 both touch
  `RefreshFlow`. With `parallelization_scope` 1 they run in sequence, in the order 02, 03, 04.
- PLAN-04 — security-sensitive: a refreshed ID token is today accepted without OIDC Core 12.2
  checks on the consumer side because the verifier is private to `TokenLifecycleManager`.
- PLAN-05 — contains a back-channel logout-token deliverable that is **gated on an operator
  decision at outline** (TokenSheriff owns it, or declares it out of scope as the client docs do
  today: CLIENT-19).
- PLAN-06 — medium-high risk (a flood bound on an unauthenticated path); opt-in only. PLAN-05 has shipped (#899), so
  its 11-file overlap is gone; the line anchors PLAN-06 cites in `JwtPropertyKeys`, `quarkus-config-doc.adoc` and the
  `doc/validation/*` files moved under it, and the outline re-reads them at HEAD.
- PLAN-07 — low priority; the operator may drop it at outline. It now carries a sixth deliverable folded from the
  PLAN-05 landing (the `LogoutTokenValidator.numericDate` resource-exhaustion defect and four minor residuals), so it is
  no longer purely hygiene: the defect ships in 0.9.10 unless PLAN-07 does, and the operator should not drop it without
  moving deliverable 6.

## Decisions

- 2026-11-12 — Epic created from the API Sheriff operator's request. Seven plans in two workstreams
  (WS-01 client seams: PLAN-01..04; WS-02 validation and shared-helper seams: PLAN-05..07), cut by
  module and by risk, not one plan per finding. `parallelization_scope` = 1 (the project default, not
  asked of the operator): most plans share `token-sheriff-client`. Target release 0.9.10 (the
  repository is at `0.9.10-SNAPSHOT`).
- 2026-11-12 — PLAN-01 shipped (PR #898, merge c23e2a85). Three-layer analysis: no blocker or major, layer 3
  clean. Inbox drain: 7 candidate-lessons dispositioned (3 promoted as lessons 2026-11-12-18-001..003; 001/002
  recurrences of lesson 2026-11-11-12-001 and 005/006 of 2026-11-11-12-003); the `doc/client/README.adoc`
  requirement-range line is now declared in the surfaces of PLAN-02, PLAN-03 and PLAN-04 (folded from 001).
- 2026-11-13 — PLAN-05 shipped (PR #899, merge fff5befe; `landings/PLAN-05.md`). Three-layer analysis: no blocker or
  major; layer 3 carries a resource-exhaustion defect in the new `LogoutTokenValidator.numericDate` (reported to the
  operator, nothing staged). Branch A (publish the logout validator and replay guard) was the OD-8 outcome. Inbox
  drain: 23 candidate-lessons dispositioned — 9 promoted as lessons 2026-11-13-06-001..009 (the rest aggregated into
  them or recurrences of 2026-11-02-14-001 / 2026-11-05-23-007 / 2026-11-11-12-003), the five `script_failure`
  clusters (017-020 and the `verdict_currency` one folded into 06-008) recorded as a Watch, not as lessons.
- 2026-11-12 — Not in this epic: anything API Sheriff can do with 0.9.9 as released (that is API
  Sheriff's PLAN-33); API Sheriff's own internal duplicates; `TokenLifecycleManager` as a replacement
  for the gateway's refresh coordinator (the manager is keyed on a server-side store and has no
  stateless cookie mode, ADR-0046 on the API Sheriff side). If the operator wants a typed-outcome
  single-flight primitive here later, it is a new plan.

## Open Defects

- Candidate defect from the audit, **not verified**: `RefreshFlow` (client) validates
  only the access token of a refresh response (`RefreshFlow` ~:196), so a consumer that does not
  use `TokenLifecycleManager` gets no ID-token or `sub` check. PLAN-04 settles it.
- 2026-11-12 — PLAN-01 landing `client-native-image-registration-008.md` is **incomplete**
  (`inbox landing-check`: `complete: false`). Missing keys: `unresolved_review_threads`,
  `review_body_findings_total`, `review_body_findings_handled` (all `unknown`; no finalize step records
  them). The provider re-read (`review-gate --pr-number 898`) shows 0 open threads and 2 coderabbit
  inline body findings (`inline-1`, `inline-2`) with no recorded disposition, while the lane narrative
  says every inline finding was answered. Sonar `sonar_open_new_issues=0` could not be re-verified
  (`undecidable`, `head_revision_unavailable`). Operator to settle the two coderabbit findings on the
  PR. See `landings/PLAN-01.md` § Routing and Merge Behavior.
- 2026-11-12 — PLAN-01 minor residuals, nothing staged (see `landings/PLAN-01.md`): (1) the delivery
  rationale at `doc/client/requirements.adoc:591` says API Sheriff's shape uses the engine without
  the client Quarkus extension — **wrong**, its pom lists `token-sheriff-client-quarkus`; (2) "SecureRandom
  holders" wording at `requirements.adoc:595` and `test-strategy.adoc:503` where the guard covers every
  static `Random`; (3) `NativeImageClassScanner:65,83` `@throws` omits `UncheckedIOException`;
  (4) two untested 400 branches in `ClientEngineNativeEndpoint:184,210`; (5) `ClientNativeImageSpecIT:23`
  imports Hamcrest against the spec constraint (module precedent). Becomes a plan, or folds into PLAN-07,
  only on the operator's word.

- 2026-11-13 — PLAN-05 layer-3 defect, reported to the operator, **nothing staged** (see `landings/PLAN-05.md` § Layer 3):
  `LogoutTokenValidator.numericDate:311-312` runs `BigDecimal.setScale(0, FLOOR)` on an attacker-sized exponent
  (`iat`/`exp` = `1e99999999`, ten characters) inside `synchronized (decisionLock)` (`:170-186`), so one signed token
  stalls every other logout validation on that validator; the lane's proposed fix covers only the positive exponent
  (a negative one takes the same path). Reachable only after the signature verified, so the signer must be the trusted
  issuer. Same area, minor: `freshnessWindow` accepts an overflowing `Duration` and `validate` then throws inside the
  lock (`:138-140,247`); a backward wall-clock step can re-admit a purged `jti` and no document says so (`:171`);
  `SignatureVerifiedToken.toString` prints `typHeader` unsanitised (`:77`); the out-of-range `numericDate` branch and the
  `fromIssuerStatus` catch are untested; three test classes exceed 500 lines. **Operator's word (2026-11-13): fold into
  PLAN-07** — now its deliverable 6 (numericDate bound for both exponent signs, `freshnessWindow` limit, the backward-clock
  statement, the `toString` exclusion, a test class for the out-of-range branch), together with the lane's other advisories
  (the untested `fromIssuerStatus` catch, the three test classes over 500 lines, the ADR-0029 revision-history note). The
  PLAN-01 minor residuals above are NOT in it and are still the operator's open call.
- 2026-11-13 — PLAN-05 landing review numbers contradicted by the provider: landing says `review_body_findings_total=1`,
  `review-gate --pr-number 899` returns 10 (nine CodeRabbit `inline-1..9`, `additional-1`), `handled` 0 with
  `merge_allowed: false, reason: body_findings_undisposed`; `unresolved_review_threads` agrees (0). `handled` is not
  re-verified. Sonar `sonar_open_new_issues=0` is `undecidable` (`head_revision_unavailable`), recorded as unknown. The
  lane's narrative shows at least six of the findings answered by fix tasks. Operator to settle the ten on PR #899.

## Watches

- Source claims in the specs are audit leads read from code only; nothing was run. Each spec labels
  them OBSERVED or HYPOTHESIS with a verify-at-outline clause. — trigger: every plan's outline.
- API Sheriff's refresh persist is presence-only while 0.9.9 compare-and-sets on the base refresh
  token (reseed fix); a stale grant overwriting a re-login mid-refresh is hypothesised, not
  verified, and is API Sheriff's to settle (its PLAN-33 D5). — trigger: PLAN-04 outline, in case a
  TokenSheriff-side compare-and-set seam is the right answer.
- PLAN-01 added `doc/developer/test-impl-mapping.adoc` rows that PLAN-06 and PLAN-07 also declare — the landing
  under-declared its surface. PLAN-05 met it on rebase and shipped with both plans' rows present; its landing does not say
  whether a conflict occurred. The watch stays for PLAN-06 and PLAN-07, which will also edit the file. — trigger: PLAN-06 and PLAN-07 landings.
- PLAN-05's capture carried five `script_failure` clusters it did not classify (`manage-execution-manifest`,
  `manage-findings`, `manage-solution-outline`, `manage-status`, and `verdict_currency`, the last folded into lesson
  2026-11-13-06-008); the argv and error are in that plan's work log, not here. Recorded instead of filed as lessons because
  nothing says caller or script. — trigger: the same notation in another plan's cluster, or the next `analyze` of a landing.
- The Standing Constraint "never gate on `test-compile` or `test`" in every spec rests on a premise main no longer holds:
  PLAN-05's `AGENTS.md` rewrite says the fixtures resolve in the reactor through `<type>test-jar</type>`. The constraint stays
  safe; whether `test-compile` now builds is not shown by reading, and the reactor and published classpaths can diverge.
  — trigger: the next plan's pre-push gate, and the PLAN-02/03/04/06/07 outlines (each re-reads `AGENTS.md` at HEAD).
- PLAN-01 native claims rest on the lane: the native IT 3/3, the removal controls and the constructor-only
  reflection sufficiency were not shown by reading. — trigger: the first native build of a consumer on 0.9.10
  (API Sheriff PLAN-34).
- A TokenSheriff change in 0.9.10 can change API Sheriff behaviour on bump: the 0.9.9 notes list
  JWKS staleness bound and re-discovery (#844), `HttpJwksLoader` status truth (#830), issuer policy
  on cache hits (#833) and DPoP/step-up changes. — trigger: each landing here; the landing report
  names what API Sheriff must re-measure. PLAN-05 adds: `MapRepresentation` record shape and member order, the new
  `SIGNATURE_ONLY_VERIFIED` event type, the opt-in `report-external-validator` / `disclose-issuer` keys (defaults
  unchanged), and the `logout` package contract (`exp == now` refused, fractional `NumericDate` kept, `"nonce":null` refused).
