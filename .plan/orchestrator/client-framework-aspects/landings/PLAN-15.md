# Landing Analysis: PLAN-15 — Close the residuals of the documentation baseline (PLAN-12 follow-up)

epic: client-framework-aspects
workstream: WS-05
pr: #954 (merged through the merge queue as `0545cffd`, 2026-11-24)

> Landing record for one shipped plan. Written by `analyze` (inbox scan) from landing message
> `plan-15-plan-12-residual-fixes-007`, corroborated against git (`0545cffd`, parent `66cecbf4`) and the CI
> abstraction. The lane's claims are leads; the facts below were read.

## Deliverable Fidelity vs Spec

Plan id `plan-15-plan-12-residual-fixes`. 6 of 6 deliverables reported done; functional review 4 met and 2 met with
residuals. `git show --stat 0545cffd`: 16 files, +90 -55; the `66cecbf4..0545cffd` range shows 17 because of two
unrelated dependabot workflow bumps (#949, #950). `git diff 66cecbf4 0545cffd -- .plan` is empty.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. `AccessTokenCache` class Javadoc states what the cache does | shipped-as-specified, with a residue (L3-1) | `AccessTokenCache.java:61-70` now says a hit re-applies expiry and maximum token age, the pipeline re-checks the loader status and runs DPoP and custom rules, and a hit does not verify the signature again. The old "never returns a token the uncached validation pipeline would reject" sentence is gone. Comment lines only. |
| 2a. General no-shim rule restored without reviving ADR-0013 | shipped-modified | `retrieval-flow.adoc:154-159` carries the rule and its scope limit. No `0013` file at `0545cffd`, and no reference to it. Two of ADR-0013's decision points are not carried over (L2-1). |
| 2b. ADR-0010 `supersedes` | shipped-as-specified | `// supersedes: 5` restored at ADR-0010 line 12 inside the metadata block; Status (line 16-18) is the single token `Accepted`. |
| 3. Three advisory documentation defects | shipped-as-specified | `BenchmarkConfiguration.defaults()` appears nowhere outside `.plan/` (the `defaults()` hits that remain belong to other classes); the unreachable wrk troubleshooting row is removed; `local-testing.adoc:28-33` names `benchmarking/scripts/serve-reports.sh`. The anchor was settled on the target side (`security-settings.adoc:81` carries `[#token-type-validation-rfc-9068]`), so `multi-issuer-setup.adoc` is not touched. |
| 4. Disposition of the 23 CodeRabbit minor comments on #952 | shipped-modified | 4 fixed in `f719225`, 15 fixed in PLAN-15, 4 declined; the table is in the PR description. The 4 declines are procedural (L1-2). |

## Three-Layer Analysis

The landing was read on three layers by one read-only leaf (`execution-context-level-3`, the `orchestrator.analyze`
surface), and the orchestrator re-read every point recorded below as `checked` at its line in the merged tree
(`git show 0545cffd:{path}`). The reading is by reading: no build, no test run, no write.

- **Dispatch:** one read-only leaf per landing — reason: the rule dispatches every full-ship landing, and this
  one touches 16 files whose statements had to be checked against code in two modules.
- **Verdict:** layer 1 findings (1 minor); layer 2 findings (3 minor); layer 3 findings (5 minor, none blocking).

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| L1-0 | Premises hold at `66cecbf4`: ADR-0010 had no `supersedes` key (it read `// supersedes: 5` at `66cecbf4^1`); `BenchmarkConfiguration` has only `builder()`; the anchor link did not resolve because the heading's auto id differs | checked | confirms | ADR-0010:6-12; `BenchmarkConfiguration.java:67`; `security-settings.adoc:81` |
| L1-1 | The Done-when is coherent and testable; the 30-file cap leaves 14 files of headroom over the 16 declared. The requirement-id check names no method | checked | confirms | spec Done-when |
| L1-2 | Deliverable 4 allows "fixed or declined with a reason", yet 4 comments were declined only because their files are outside the Expected Surface, while the lane's own table says each still applies at HEAD. The 30-file cap would have admitted them. The spec's verify-first clause ("stop and report instead of widening") produced a procedural decline, not a substantive one | checked | refutes | `token-handling.adoc:114-115` and `:118-120`; `benchmarking/README.adoc:43`; `performance-scoring.adoc:136` (finding `-001`) |

**Layer 1 verdict:** findings — 1 (L1-2, minor).

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| 1 | `AccessTokenCache.java:61-70` | none (comment only; spec forbids tests) | none | checked |
| 2 | `retrieval-flow.adoc:154-159`; ADR-0010:12 | none (documentation) | L2-1 | checked |
| 3 | `benchmarking-common/README.adoc:168-173`; `benchmark-integration-wrk/README.adoc:297-301`; `local-testing.adoc:28-33`; `security-settings.adoc:81` | none | L2-2 | checked |
| 4 | ADR-0003, -0017, -0031; `transport.adoc`; `test-impl-mapping.adoc`; `security-settings.adoc`; three Quarkus guides; `JFR-Instrumentation.adoc` | none | L2-3 | checked |

Every one of the 16 touched files maps to a deliverable; none is unexplained. The requirement-id set is unchanged: no
added or removed diff line contains a `CLIENT-`, `VALIDATION-` or `COMMONS-` id (compared on diff lines, not on a
sorted dump of both trees). No test file or non-comment Java line is touched.

- **L2-1 (minor).** The restored rule at `retrieval-flow.adoc:154-159` keeps the refusal, the `@Deprecated` refusal
  and the scope limit. It drops two decision points of the deleted ADR-0013 (`66cecbf4^1`, decision points 1 and 2):
  "source compatibility is not the acceptance test" and "a deprecation window would not narrow the exposure, only
  date it". Only the half-sentence "keeps that value reachable for as long as it exists" remains. Deliverable 2
  named the first of them. The Done-when (rule plus scope limit) is met; the normative text is thinner than the
  ADR it replaces, so the standing constraint "never lose a normative statement" is deviated from.
- **L2-2 (minor).** `local-testing.adoc:28-33` now writes `benchmarking/scripts/serve-reports.sh stop`, which works
  from any directory. The Quick Start block just above still does `cd benchmarking/scripts`, so a reader following
  in order has changed directory twice. Correct, but awkward.
- **L2-3 (info).** The Failsafe skip-binding rule in `test-impl-mapping.adoc:492-512` was generic and is now scoped to
  `token-sheriff-client`, with `token-sheriff-quarkus-integration-tests` as the named exception. Accurate against both
  POMs; any other module with a Failsafe tier is no longer covered by a stated rule.

**Layer 2 verdict:** findings — 3 (L2-1, L2-2 minor; L2-3 info).

### Layer 3 — Correct

None blocking. Five defects in the merged text, each re-read at its line:

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `AccessTokenCache.java:44-45` and `:70` | A reader takes "maintaining security through raw token verification on cache hits" and "verifies token content on each hit" as the hit's guarantee | Both describe only the hash-collision guard (`verifyToken`, line 210). Placed beside the new disclosure that a hit skips signature verification, they read as a stronger guarantee than the cache gives, which is the misreading PLAN-15 set out to close. Not false | minor |
| L3-2 | `doc/validation/architecture.adoc:36` | A reader reads the last sentence of the paragraph | "The window ends when the token expires or is evicted" omits maximum token age and a DPoP or custom-rule rejection, which the same paragraph (and the Javadoc) name as re-applied. The Javadoc is the more complete of the two | minor |
| L3-3 | ADR-0010 `:63-66` | A bearer-only or mTLS client; a `5xx` that carries a `DPoP-Nonce` header | The new sentence states the single nonce retry without its conditions. `TokenEndpointClient.java:253-265` and `:592-599` retry only for a DPoP sender constraint, with a non-blank header free of CR/LF/NUL, and never after an `invalid_scope` answer. Without them the sentence is wrong for every non-DPoP client. Named by the lane as open | minor |
| L3-4 | `doc/validation/architecture.adoc:262` (and the `maxPayloadSize` table row) | A reader reads `maxPayloadSize` | "bounds token parts only" now contradicts `security-settings.adoc:56`, which says it also bounds string-supplied JWKS (`JwksParser.validateContentSize`, `JwksParser.java:181-190`). Named by the lane as open | minor |
| L3-5 | ADR-0017 `:21-27` | A reader reads the readiness sentence | The facts are right (`JwtPropertyKeys.java:956,970`), but one em-dash parenthetical runs into a second em-dash clause, so two statements read as one. Readability only | minor |

**Layer 3 not shown by reading:** the Asciidoctor render of the `token-type-validation-rfc-9068` anchor (by reading,
it is defined once and linked once at `multi-issuer-setup.adoc:132`); the lane's claim of a full verify (5839 tests)
and a clean AsciiDoc link check (not re-run); the content of the 10 bot-finding fixes in `74e1f9b3` (not diffed).

**Layer 3 verdict:** findings — 5 (all minor); not shown by reading — 3.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| `CLAUDE.md` | Documentation-only plan: one Javadoc edit, no behaviour or test change | conforms | comment lines only in `AccessTokenCache.java` |
| spec Standing Constraints | Requirement ids frozen | conforms | no id token in any added or removed line |
| spec Standing Constraints | ADR numbers never reused; a deleted ADR is not revived | conforms | no `0013` file at `0545cffd`; the rule lives in `retrieval-flow.adoc` |
| spec Standing Constraints | Never lose a normative statement | deviates | L2-1; and the Failsafe rule narrowed (L2-3) |
| spec Standing Constraints | Do not touch `.plan/`; PR 30 files or fewer | conforms | empty `.plan` diff; 16 files |
| `AGENTS.md` | the code rules it names | not_shown_by_reading | not read by the leaf; a documentation-only diff makes a deviation unlikely |

## Metrics and Anomalies

- Tokens: 19,824,494 total; wall time 14,174 s (about 3.9 h). `effort` was never stamped (`unknown`).
- Review rounds: three full finalize review rounds plus one inline correction (candidate lesson `-004` puts a full
  round at about 0.5M tokens). One extra pre-merge loop-back came from CodeRabbit re-editing its walkthrough after
  the PR-body update (candidate lesson `-003`).
- Anomalies: the first CI run failed on a transient Maven Central HTTP 429 in `build / quarkus-alignment` and passed
  on re-run. The plan was stamped plugin 0.1.2133 while 0.1.2184 is installed. A commit without a sanction was
  refused by the finalize commit guard once, then recovered (candidate lesson `-006`).
- The landing message carries `steps` without `archive-plan`: it had not run when the message was written.

## Routing and Merge Behavior

- Review: CodeRabbit and Sourcery participated before the enqueue; the pending pr-comment finding count was 0 at the
  enqueue. 10 bot findings on #954 were fixed in `74e1f9b3`. Re-read at analysis (`runbook_lane review-gate
  --pr-number 954`, 2026-11-24): `unresolved_review_threads: 0`, `review_body_findings_total: 10`,
  `review_body_findings_handled: 0` with `merge_allowed: false`, reason `body_findings_undisposed`. The landing
  carries all three as `unknown`, so there is no number to contradict. `review_body_findings_handled` is **not
  re-verified**: the plan's progress file is archived and the 10 findings' dispositions are not readable from it; the
  lane's own account is that all 10 were fixed. The gate's `0` is therefore not evidence that they were not.
- CI/merge: merged through the merge queue as `0545cffd`. The first CI run failed on the transient 429 and passed on
  re-run. The post-merge JMH benchmark job was still running and is not a required check; the lane's post-merge wait
  timed out on it alone after 757 s.
- Sonar: the landing's `sonar_open_new_issues` is `unknown` (the lane's roundtrip counted 0 new-code issues but did
  not read hotspots). The re-read at analysis (`read_open_issues`, project `cuioss_TokenSheriff`, PR 954) is
  `undecidable` (`head_revision_unavailable`; `analysis_revision` is `74e1f9b3`). So the Done-when "zero new-code
  issues and zero hotspots" is **not evidenced**; recorded as `unknown`, never `0`. No open rule is named.
- Collision: none observed. PLAN-15 ran alone, as sequenced.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-15 --status shipped`
- [x] row `pr` stamped — `954`
- [x] row `landing` stamped — `landings/PLAN-15.md`
- [x] row `plan_marshall_plan_id` stamped — `plan-15-plan-12-residual-fixes`
- [x] epic.md: PLAN-12 layer-3 entry retired (PLAN-15 landed); PLAN-15 layer-3 and layer-2 findings and the
      declined-comment finding recorded as Open Defects; queue annotation for PLAN-15; held-lesson entry extended
- [x] inbox: `-001` (absorbed as an Open Defect) and `-007` (this landing) archived
- [x] resume anchor updated; `queue-view.md` regenerated

## Follow-Ups

- **Layer-3 defects L3-1..L3-5 and layer-2 L2-1 (documentation text).** Reported to the operator; not fixed inline;
  not staged. A staged fix-plan spec follows only on the operator's word.
- **Finding `-001`:** the 4 declined comments (`token-handling.adoc` x2, `performance-scoring.adoc:136`,
  `benchmarking/README.adoc:43`) and 3 code-side follow-ups (ADR-0031 merged-scope deployment check;
  `JfrBenchmarkRunner.createConfiguration` overriding `jmh.*`; `DiscoveryResolver.validateEndpointSchemes` hand-listed
  endpoints) recorded as one Open Defect. The three code-side items are product code, so they belong to a spec of
  their own, not to PLAN-13 or PLAN-14 (those change ids only).
- **Candidate lessons `-002` to `-006`:** held in `inbox/` with the seven from PLAN-12; the same `wrong_store`
  refusal applies and the same operator decision covers all twelve. `-005` record 3 independently confirms the
  `--changed-paths-file` drift of held PLAN-12 message `-007`.
