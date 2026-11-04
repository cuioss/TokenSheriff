# Landing Analysis: PLAN-09 — DPoP and token-parser hardening edges

epic: quality-report
workstream: WS-01
pr: 851 (https://github.com/cuioss/TokenSheriff/pull/851)

> Landing record for one shipped plan. Written by the `analyze` verb (inbox scan, message
> `plan-09-dpop-parser-hardening-007.md`) after verifying the claims against the merged source, the PR state read
> through the CI abstraction, and SonarCloud. A landing message is a lead, not a fact.

## Deliverable Fidelity vs Spec

Corroborated against `origin/main` at the merge commit `49ba673d` (read: `git show`, `git grep`, the 21-file diff
against the first parent) and the PR (`ci pr view --pr-number 851`: `state: merged`, `merge_commit_sha`
`49ba673dd1fca6b74fad6e9c6b6fbb114e99388d`).

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. V-11 shared ECDSA range check, wired DPoP test | shipped-as-specified | `SignatureVerificationUtil.verifySignature` calls `checkEcdsaComponentRange` (lines 91-92) for every ECDSA algorithm, so both signature paths share it; `TokenSignatureValidator` lost its private copy (57 lines out). `WiredNegativePathAttackTest` gained 53 lines (VTEST-16, all-zero ES256 DPoP proof through `TokenValidator`). |
| 2. V-12 replay store last | shipped-as-specified | `DpopProofValidator` line 344: `replayProtection.checkAndStore(jti)` runs after every stateless check, with a comment saying why; `DpopProofValidatorTest` has "A thumbprint-mismatch proof leaves no replay entry" (V-12). |
| 3. V-17b `split("\\.", -1)` | shipped-as-specified | All three production sites use limit `-1`: `NonValidatingJwtParser` lines 298 and 412, `DpopProofValidator` line 251. No single-argument split remains in the module's main sources. |
| 4. V-17c inclusive expiry boundary | shipped-as-specified | `ValidationContext.isExpired` is now the shared predicate `!expirationTime.plusSeconds(skew).isAfter(now)` (line 153), so `now == exp + skew` is expired. The PR says the cache shares it; not independently traced. |
| 5. V-17e EC JWK `alg` against `crv` | shipped-as-specified | `KeyProcessor` lines 177-194 derive the curve algorithm and drop a JWK whose `alg` disagrees. `KeyProcessorTest` is new (132 lines), as the spec's claim label predicted. |
| 6. Decode-mode gap (PLAN-21 residual) | shipped-as-specified | `NonValidatingJwtParser` changed by 170 lines; `NonValidatingJwtParserTest` (+177) and `NonValidatingJwtParserJweTest` (+115) carry the cross-mode rows. The behaviour itself was not re-read. |
| (landing counts 7 deliverables) | unverified | The spec lists six; the landing reports `deliverables_total=7`, `deliverables_done=7` and does not name the seventh. |

**Added beyond the spec.** A DPoP proof whose JWK `alg` and `crv` disagree was accepted before and is now rejected
(`DpopProofValidator`, commit `ca342f9c` per the landing). Corroborated in the PR body and in the merged code path
only by the `alg`/`crv` check being present; the commit itself was not read.

**Surface delta (landing-check, `expansion_detected`).** Declared 11, realized 21, 11 added, 1 missing. Added:
`doc/validation/cve-lessons-register.adoc`, `doc/validation/specification/microprofile-jwt-compatibility.adoc`,
`doc/validation/threat-model.adoc`, `JwtPropertyKeys.java`, `IssuerConfig.java`, `UncheckedThrowBoundaryTest.java`,
`KeyProcessorTest.java`, `NonValidatingJwtParserJweTest.java`, `SignatureVerificationUtilTest.java`,
`ExpirationValidatorTest.java`, `WiredNegativePathAttackTest.java`. Missing: `JwksParserTest.java` (declared, never
touched). Eleven of the added files are review-driven or consequences of the new `KeyProcessorTest`; the declared
surface was therefore short by the three prose restatements of the expiry boundary (candidate lesson 001), the
review-driven tests and the register and threat-model rows. The shipped spec is a record and is not edited.

## Metrics and Anomalies

- Tokens: 37,464,467 (the highest in the epic so far; PLAN-21 and PLAN-08 were 24 to 31 million).
- Duration: 346,688 wall seconds, about 4.0 days (landing message; the pause content is not itemised).
- Anomalies:
  - A pending 5-execute scope-creep warning (`364f9d`, six files outside declared scope, threshold five) blocked the
    first merge attempt at the pre-merge findings gate. It was accepted with a per-file rationale and the merge was
    re-run once (branch-cleanup loop-back 1 of 5). The six files match the surface delta above.
  - `effort=unknown` again: the landing is incomplete on the `effort` key (see Open Defects in `epic.md`).
  - Two metrics rows carry placeholder figures that a corrected row replaced (landing message; not checked).
  - Test counts differ between the two sources: the landing says the whole-tree verify ran 3592 tests, the PR body
    says 3568. Neither was re-run; the difference is recorded, not resolved.

## Routing and Merge Behavior

- Review: CodeRabbit findings `abb54d`, `d0fb52`, `90282a`, `b6a5a2` were fixed by TASK-14 to TASK-18, per the
  candidate-lesson messages; the landing's `code-review` step reports 0 blocking and 1 advisory, 1 rule cited.
  Functional review: 7 of 7 deliverables met, no residuals, no unevidenced criteria. Landing message; not
  independently verified.
- Code-review advisory, narrowed: the landing and the PR body say the non-EC-key and null-domain-parameters guards
  in `SignatureVerificationUtil.checkEcdsaComponentRange` are shadowed by the earlier `initVerify` call. **The merged
  code contradicts that.** The range check runs BEFORE `initVerify` (lines 91-92 against 93), which is exactly the
  fix candidate lesson 003 describes, and `SignatureVerificationUtilTest` has `shouldRejectNonEcKey` and
  `shouldRejectNullSignature`. What remains unverified: no test in that class names the `params == null` branch, and
  the claim that `PsychicSignatureAttackTest` cannot fail without the explicit range check (the JDK rejecting
  all-zero signatures itself) was not tested. Recorded as a Watch.
- CI/merge: all 23 PR checks green (landing message). Merged through the merge queue as `49ba673d`; read from the
  PR as `state: merged` and confirmed on `origin/main`. The post-merge `Run JMH Benchmarks` workflow had not
  finished when the run's wait hit its deadline; not re-read here.
- Sonar: gate `OK` on PR 851 (read directly: new coverage 91.1, duplication 0.0, security hotspots reviewed 100,
  all three ratings 1), and a direct `resolved=false` query on the PR returns 0 open issues. `sonar
  read_open_issues` returned `undecidable` (`head_revision_unavailable`), as for the earlier PRs. The default branch
  still shows 44 open issues (the PLAN-23 backlog, unchanged); this PR adds none. PR-scoped snapshots, not re-runs.
- Merge-queue squash message is platform-composed; the repository default was not changed (landing message).

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-09 --status shipped`
- [x] row `pr` stamped — value 851
- [x] row `landing` stamped — value `landings/PLAN-09.md`
- [x] row `plan_marshall_plan_id` already stamped `plan-09-dpop-parser-hardening`; matches the sender, no change
- [x] `findings-disposition.md`: V-11, V-12, V-17b, V-17c, V-17e marked FIXED; the decode-mode item has no finding ID
- [x] epic.md: Decisions entry, Open Defect for the unstamped `effort`, Watches for the PLAN-09 residuals, the
      single narrowed advisory, and the Queue annotations
- [x] six candidate-lesson messages dispositioned (see Follow-Ups)
- [x] resume anchor updated in `resume_anchor.md`; `queue-view.md` regenerated

## Follow-Ups

- Lessons 002 (COVERED register row needs a wired test on every path) and 005 (the DPoP proof path must enforce the
  same key checks as the access-token path) are project-owned and were promoted into the local lesson store. Lessons
  001, 003, 004 and 006 name `plan-marshall:*` or marketplace components and went into the 2026-11-04 addendum
  of `upstream-plan-marshall-report.md`; nothing was sent.
- PLAN-09 changed files that five staged specs also declare: PLAN-15 (`ExpirationValidatorTest.java`), PLAN-17
  (`IssuerConfig.java`, `NonValidatingJwtParser.java`), PLAN-18 (`threat-model.adoc`), PLAN-19 (the register, the
  MicroProfile spec, `threat-model.adoc`) and PLAN-23 (`ValidationContext.java`, `WiredNegativePathAttackTest.java`).
  Those specs were grounded before this merge, and each re-reads those files at its own outline. PLAN-23's Sonar
  table in particular names issues by file; the PLAN-09 diff touches two of its files, so its issue keys and line
  numbers are worth a re-read before launch. No concurrent collision occurred: PLAN-10 was clean against PLAN-09's
  footprint.
- The `exp` boundary now being inclusive changes what `ExpirationValidatorTest` pins; PLAN-15 adds fixed-clock tests
  in the same area and should read the new rows first.
