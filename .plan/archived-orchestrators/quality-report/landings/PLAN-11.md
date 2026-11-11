# Landing Analysis: PLAN-11 — Audience semantics (empty `aud` array, list-typed `azp`)

epic: quality-report
workstream: WS-01
pr: 853 (https://github.com/cuioss/TokenSheriff/pull/853)

> Landing record for one shipped plan. Written by the `analyze` verb (inbox scan, message
> `plan-11-audience-semantics-003.md`) after verifying the claims against the merged source, the PR state read
> through the CI abstraction, and SonarCloud. A landing message is a lead, not a fact.

## Deliverable Fidelity vs Spec

Corroborated against `origin/main` at the merge commit `a9ce05df` (read: `git show`, `git grep`, the 10-file diff
against the first parent) and the PR (`ci pr view --pr-number 853`: `state: merged`, `merge_commit_sha`
`a9ce05df8f15b003ab1d3d36d8677f27104ad015`).

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. V-17f empty `aud` = present-but-mismatched, never eligible for the `azp` fallback | shipped-as-specified (validator-local, as the spec preferred) | `AudienceValidator.validateAudience` now tests `audienceClaim.isEmpty()` only; the `|| isNotPresentForClaimValueType()` clause is gone (the diff shows the one-line change). An empty list therefore reaches `validateStringListAudience`, finds no match and throws `AUDIENCE_MISMATCH`. `ClaimValue` is untouched, so the shared predicate is unchanged. |
| 2. TV-2 parameterized test, ACCESS/ID token x {fallback on + azp, fallback off, no azp}, outcome and counter | shipped-as-specified | `AudienceValidatorTest.shouldRejectEmptyAudienceArrayAsMismatch` is `@ParameterizedTest` over `Stream.of(ACCESS_TOKEN, ID_TOKEN)` x `AzpSituation`; `assertEmptyAudienceRejectedAsMismatch` asserts the event type, `AUDIENCE_MISMATCH == 1`, `MISSING_CLAIM == 0` and `ACCESS_TOKEN_AUDIENCE_MISSING == 0`. A second test, `shouldRejectEmptyAudienceArrayEvenWhenAudienceOptional`, covers `accessTokenAudienceOptional(true)`. The three-situation enum and the fallback-off arm were not individually read. |
| 3. gap VAL 4 `azp` as JSON array and as non-string must reject | shipped-modified (a fix, not only tests) | New `StrictStringMapper` (`domain/claim/mapper/`, 60 lines) returns `null` for an absent claim, `ClaimValue.forPlainString` for a JSON string and throws `TokenValidationException(MISSING_CLAIM)` for anything else; `ClaimName.AUTHORIZED_PARTY` maps through it (`ClaimName.java` line 166). Tests: `StrictStringMapperTest` (+117), `AuthorizedPartyValidatorTest` (+27), `AudienceValidatorTest.shouldNeverSatisfyAudienceFallbackWithNonStringAzp` (ACCESS/ID x JSON type), and the `JsonTypedAzpFixture` helper (+128). |
| (spec hypothesis) `MandatoryClaimsValidator` also treats `aud: []` as missing | refuted | The merged `MandatoryClaimsValidator.java` has no reference to the audience claim at all (`grep` for `aud`/`isNotPresent`/`isPresent` returns nothing), and the PR did not touch it. The spec's `verify-at-outline` item resolved to "no change". |

**Added beyond the spec.** Native-image metadata for the new mapper (`reflect-config.json`, 4 lines, covered by one
added line in `NativeImageMetadataTest`) and the `security-reference.adoc` paragraph (5 lines; the spec named this file
as "adjacent, allowed"). The reflect-config registration is the consequence of adding a class that `ClaimName`
instantiates; it matters in the native build and was not exercised here.

**Surface delta (landing-check, `unmeasured`: the landing supplied neither side).** Computed here from the spec and
the merged diff: declared 5, realized 10, 8 added, 3 missing. Added: `ClaimName.java`, `StrictStringMapper.java`,
`reflect-config.json`, `NativeImageMetadataTest.java`, `StrictStringMapperTest.java`, `AuthorizedPartyValidatorTest.java`,
`JsonTypedAzpFixture.java`, `doc/validation/security-reference.adoc`. Missing: `MandatoryClaimsValidator.java`,
`MandatoryClaimsValidatorTest.java` (both hypotheses refuted, as above) and `ClaimValue.java` (a hypothesis the spec
itself marked "prefer a validator-local change"). Only the `AudienceValidator` pair matched. The under-declaration is
the `azp` half: the spec said "tests (and fix if needed)" and the fix needed a new mapper, a `ClaimName` edit and
native metadata. The shipped spec is a record and is not edited.

## Metrics and Anomalies

- Tokens: 9,081,038 (the lowest of the code plans so far; PLAN-09 was 37.5 million, PLAN-21 and PLAN-08 24 to 31 million).
- Duration: 11,397 wall seconds, about 3.2 hours (landing message).
- Effort: `medium`, stamped (the landing is complete; first complete landing since PLAN-07 and PLAN-20).
- Anomalies:
  - The pre-push gate's stand-alone Maven `test-compile` cannot resolve the validation test-jar before `package`; the
    failed run was parsed and auto-filed as 120 `build-error` findings that blocked the first merge dispatch until each
    was resolved as `rejected`. The operator counted the whole-tree `verify -Ppre-commit` (3621 tests per the landing;
    not re-run) as the cover. Sixth plan with this degradation, and the first where the findings batch itself blocked a
    merge (see the upstream addendum).
  - The landing's `pre-push-quality-gate:done` is therefore a recorded-degraded step, not a clean gate.

## Routing and Merge Behavior

- Review: the landing's `code-review` reports 0 blocking and 3 advisory, 3 rules cited; functional review 3 of 3
  deliverables met, no residuals, no unevidenced criteria (landing message; not independently verified). The three
  advisories are in the PR body and the landing residue: the non-string `azp` tests assert only the exception
  supertype; `client_id` still stringifies non-string values; a `StrictStringMapper` rejection is neither counted nor
  logged (the exception is thrown with `MISSING_CLAIM`, which the pipeline counts only if it catches it on the
  existing malformed-claim path; not traced).
- ADR step: one borderline proposal (claim type strictness at claim mapping), declined by the operator; no ADR.
- CI/merge: all 24 PR checks green (`ci checks status`: `overall_status: success`, `Run JMH Benchmarks` included;
  `deploy-snapshot` and `covering-run-check` skipped by design). Merged through the merge queue as `a9ce05df` and read
  back from the PR as `state: merged` and from `origin/main`. The merge was authorised by the operator for this PR;
  `marshal.json` line 133 has `final_merge_without_asking: true`, which does conflict with the repo's "no auto-merge
  unless told" rule in `CLAUDE.md` (verified; recorded as a Watch).
- Sonar: gate `OK` on PR 853 read directly (new coverage 100.0, duplication 0.0, security hotspots reviewed 100, all
  three ratings 1), and `search --pr 853` returns 0 issues. A PR-scoped snapshot, not a re-run. `main` still shows its
  44 open issues (the PLAN-23 backlog); this PR adds none.
- Concurrency: PLAN-11 ran beside PLAN-23 and PLAN-10. No collision was observed. Its realized footprint touched no
  file either declares (PLAN-10's and PLAN-23's surfaces were not re-diffed here; the landing and the merge reported none).

## Verified residual: the `client_id` fallback is still string-coerced

Read in the merged source: `AuthorizedPartyValidator` (line 100) falls back to `ClaimName.CLIENT_ID` when `azp` is
absent and the RFC 9068 fallback is on, and `CLIENT_ID` is still mapped by `IdentityMapper`, which stringifies any
non-string value (`value.toString()`, with a comment calling that intentional). So a numeric or list-typed `client_id`
on an `azp`-less token can still satisfy the same configured-id comparison PLAN-11 closed for `azp`. That is the
operator's recorded decision (per the landing and PR body), but it is a security-relevant residual of the same
finding class and **no staged spec covers it** (PLAN-10 mentions `client_id` only for PAR). Recorded as a Watch and an
open operator item.

Also verified: `ClaimValue.isNotPresentForClaimValueType()` has no production caller left (only `ClaimValueTest`
exercises it). Dead code after this change; a candidate for PLAN-23-style cleanup, not acted on.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-11 --status shipped`
- [x] row `pr` stamped — value 853
- [x] row `landing` stamped — value `landings/PLAN-11.md`
- [x] row `plan_marshall_plan_id` stamped `plan-11-audience-semantics` (it was empty since the start; the sender in
      the message matches the plan directory name)
- [x] `findings-disposition.md`: V-17f, TV-2, gaps VAL 1 and VAL 4 marked FIXED
- [x] epic.md: Decisions entry, Queue annotation, Watches (the `client_id` residual, the dead predicate, the merge
      config conflict), no new Open Defect (the landing is complete)
- [x] two candidate-lesson messages dispositioned (see Follow-Ups)
- [x] resume anchor updated in `resume_anchor.md`; `queue-view.md` regenerated

## Follow-Ups

- Lessons 001 (an outline's conditional "also change F if needed" clause under-declares the footprint) and 002 (a
  stand-alone `test-compile` gate cannot work in a test-jar reactor, and one failure becomes 120 findings) both name
  `plan-marshall` mechanisms (the Q-Gate `scope_criterion_validator`, the phase-4 `declared_set_closure` check, the
  pre-push gate, build-maven canonical resolution). Both recur items already in the upstream report (001: the
  "conditional changes" items near PLAN-09-001; 002: PLAN-21-001 and PLAN-04-008). Both went into a 2026-11-04
  addendum of `upstream-plan-marshall-report.md`; nothing was sent and no local lesson was filed. The project half of
  001, "a new `LogRecord` always pulls in `doc/LogMessages.adoc`", is already a Standing Constraint in every spec.
- PLAN-11 changed files that staged specs also declare: PLAN-18 and PLAN-19 (`doc/validation/security-reference.adoc`).
  Both re-read that file at their own outline; the added paragraph is the empty-`aud` and string-only `azp` semantics.
- No staged spec declares `AudienceValidator.java`, `ClaimName.java` or `reflect-config.json`.
