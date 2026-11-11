# Landing Analysis: PLAN-19 — Documentation structure and hygiene

epic: quality-report
workstream: WS-05
pr: #879 (merge `d1af3ebb76849e3c69e8d82f31ac68cd2120a7f8`, merged 2026-11-06T22:30:30Z)

> Landing record for one shipped plan. Written by the `analyze` verb (inbox scan) after verifying the landing message
> against git, the PR and the merged tree. The inbox landing was `plan-19-doc-structure-hygiene-012.md`; its
> `landing-check` reported `complete: false` (missing key `effort`, written as `unknown` by the producer), so the landing
> is reconciled as far as it goes and recorded as an Open Defect (fourteenth producer of an unstamped effort).

## Deliverable Fidelity vs Spec

Corroborated against the real merge diff (`d1af3ebb`, 50 files, +242/-157, documentation only, no `.plan/` path and no
`src/` path) and the merged tree. The lane reports six of six deliverables done (`deliverables_total=6`); the spec has five.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. D-5 / Q-12 `AGENTS.md` | shipped-as-specified | No `/Users/` or `/home/` path remains in any `*.adoc` or `*.md`; `token-sheriff-client/` is listed (line 65); the file is named `README.md`; the skills list equals the three `.claude/skills/` directories (leaf). |
| 2. D-7 / D-10 hub, ADR-0002, benchmarking LogMessages | shipped-as-specified | `doc/README.adoc` links all 26 ADRs, every target exists and every title matches its `= ADR-NNNN:` heading; the `cve-lessons-register` entry is at line 71; ADR-0002 reads `Accepted`; the benchmarking-common `LogMessages.adoc` is retitled (leaf). |
| 3. D-11 `<<>>` to `xref:` and the blank line | shipped-modified | No `<<` cross-reference is left in docs (`git grep` re-run: only `AGENTS.md:415` prose and a shell heredoc in the release skill). The blank-line half did not exist at base: every `* Source:` line of `doc/client/requirements.adoc` already had a blank line before it at `d0fa0c7f` (re-read); the lane rewrapped one `+ PKCE flow` line instead and recorded neither. Four `<<` sites outside the "12 files" were also converted (Analysis-03.2026-Integration, quarkus-test-setup, native-optimization-guide, the integration-tests README), plus `roles-groups-mapping.adoc`. |
| 4. D-12 prose "Token-Sheriff" | shipped-as-specified | No prose "TokenSheriff" remains; the surviving hits are log identifiers, Sonar badge keys and one path, and the rename altered no identifier, URL or config key (leaf). |
| 5. D-15 client-quarkus README, "cui-http-client" | shipped-modified | The new README names classes, the `sheriff.client` prefix and links that all exist (leaf); no "cui-http-client" is left. The same files' TLS and OAuth 2.1 sentences were rewritten beyond the rename (see layer 2). |
| (not in spec) 6. ADR statuses | added-unplanned | 22 implemented Proposed ADRs set to Accepted on the operator's answer to an outline question (lane report; the answer itself is unverified here). The merged tree holds 25 `Accepted` and one `Superseded by ADR-0010` (re-read). Three were spot-checked against the code by the leaf (ADR-0022, 0024, 0025: implemented). ADR-0007, 0010 and 0026 also carry body corrections made after review comments. |

## Three-Layer Analysis

- **Dispatch:** one read-only leaf per landing (`execution-context-level-3`, resolved on the `orchestrator.analyze` surface, source `plan.effort`) — reason: testing the rewritten ADR guarantees and the new index against the implementing classes across 50 files is the depth the dispatch rule exists for, and this is the only landing in the drain. The leaf wrote nothing. The orchestrator re-read at `file:line` against `main` (`d1af3ebb`): `doc/commons/requirements.adoc:49-54`, `doc/commons/specification/transport.adoc:52-56`, `doc/commons/threat-model.adoc:125-128`, `token-sheriff-quarkus-parent/README.adoc:12-16`, `RefreshFailureClassification.java:28-34,76-79`, `RefreshFlow.java:86-88,276-278`, `doc/adr/0010-…:185-189`, `doc/adr/0005-…:55`, `microprofile-jwt-compatibility.adoc:155`, a `git grep` for `<<`, a `git grep` for "blacklist", an `awk` over every ADR's Status line, and the `* Source:` lines of `doc/client/requirements.adoc` at `d0fa0c7f`. Not re-read, so recorded as leads: the ADR-0007 and ADR-0026 code comparisons (`StoredToken.java:174-182`, `TokenValidatorStartupInitializer.java:64,95`), the 20-plus xref anchor checks, the climb of the `RefreshRedemption`, `TokenLifecycleManager`, `CredentialRejectedException`, `ClientAuthenticationFailedException` and `token-handling.adoc` sites of the same "untouched and still valid" wording, and the three-ADR status spot-check.
- **Verdict:** layer 1 findings (1 major, 1 minor); layer 2 findings (1 major, 2 minor, 1 not shown by reading); layer 3 findings (3 minor, 2 info, 1 not shown by reading). No blocker.

### Layer 1 — Specified

| Finding | Claim | Verdict | Evidence (file:line) |
|---------|-------|---------|----------------------|
| L1-1 | The spec's blank-line premise for `doc/client/requirements.adoc` was already stale at base, so that half of deliverable 3 could not be met as written and its Done-when is vacuous (major: a spec premise that was wrong) | checked | `git show d0fa0c7f:doc/client/requirements.adoc` lines 85-96 (re-read): a blank line precedes every `* Source:` |
| L1-2 | Expected Surface listed 20 paths plus 2 HYPOTHESIS; the diff touches 50. 33 files are undeclared: 25 ADRs (only ADR-0002 was declared), `benchmarking-common/README.adoc`, `Analysis-03.2026-Integration.adoc`, `token-sheriff-quarkus-parent/README.adoc`, `quarkus-test-setup.adoc`, `native-optimization-guide.adoc`, the integration-tests README. The fourth under-declared landing of the epic (PLAN-17 was the third) | checked | `plans/PLAN-19-doc-structure-hygiene.md` Expected Surface; `git show --stat d1af3ebb` |
| L1-3 | The spec has no deliverable for the ADR status flips, the ADR body rewrites or the TLS and OAuth 2.1 sentence rewrites, which the lane did | checked | spec section list; landing message |

PLAN-19 is shipped, so no staged spec carries these claims and `corpus set-verdict` persists nothing for it.

**Layer 1 verdict:** findings — 1 major (L1-1), 1 minor (L1-2), 1 minor (L1-3)

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|-----------|--------------------|---------------------------------|---------|
| 1 | `AGENTS.md` | none (documentation) | none | checked |
| 2 | `doc/README.adoc`, ADR-0002, benchmarking `LogMessages.adoc` | none | none | checked |
| 3 | 16 files converted | none; the spec's "AsciiDoc link verification, zero broken links" has no evidence in the lane report or diff (no build, no tool run) | blank-line half replaced by a rewrap (L2-1) | not_shown_by_reading |
| 4 | prose in the touched files | none | none | checked |
| 5 | new README; `doc/validation/{requirements,security-reference,threat-model}.adoc` | none | the HTTP/TLS opt-in text and the OAuth 2.1 HS256 paragraph were rewritten beyond the rename; both read as true against the code (`allowInsecureHttp` on `HttpJwksLoaderConfig:446` and `WellKnownConfig:158`, `JwtPropertyKeys:517`, `RejectedAlgorithms:37`) but are undeclared scope (L2-3) | checked |
| 6 (added) | 22 ADR files, Status line | none | no spec section authorises it (L2-2) | checked |
| lane report | "no build, no test, no Sonar: documentation-only" | n/a | the lane's own residue says the pre-push gate and push freshness gate were recorded "no build owed", not green, and the Sonar count was undecidable and accepted by the operator | checked |

L2-1 (minor): the only non-xref edit in `doc/client/requirements.adoc` is the `+ PKCE flow` rewrap at line 71, which the spec and the lane report omit. L2-2 (major): the 22 status flips have no spec authority; the merged tree is consistent (all `Accepted`), so this is a process finding, not a defect. L2-3 (minor): the validation-doc claim rewrites are undeclared scope.

**Layer 2 verdict:** findings — 1 major (L2-2), 2 minor (L2-1, L2-3), 1 not shown by reading (deliverable 3 link verification)

### Layer 3 — Correct

The changed document text was read as claims against the implementing code. The three rewritten ADR guarantees (0007 identity binding, 0010 classification, 0026 startup fail-fast) match the code, the 26-row index and the new client-quarkus README are accurate, and no xref target is broken by reading. What the landing left, or the new text newly contradicts, is documentation drift (no executable line changed):

| Defect | File:line | Effect | Severity |
|--------|-----------|--------|----------|
| L3-1 | `doc/commons/requirements.adoc:51-52`, `doc/commons/specification/transport.adoc:54`, `doc/commons/threat-model.adoc:63-64,127` | COMMONS-2 "Plain HTTP endpoints MUST be rejected", the transport sentence and T-TLS carry no `allowInsecureHttp` opt-in, while the same landing's `security-reference.adoc:52` and `threat-model.adoc:45,189` document it and `transport.adoc:64,97,101` already describe it. A reader of COMMONS-2 is told a configuration is impossible that the code accepts. The lane's own follow-up, confirmed; the commons threat-model line is new (re-read) | minor (the leaf rated COMMONS-2 major) |
| L3-2 | `token-sheriff-quarkus-parent/README.adoc:14` | credits `ClientRuntimeConfig` with the "configuration mapping"; it holds the key constants and `ClientConfigMapper` maps. The new client-quarkus README names `ClientConfigMapper`, so the two READMEs disagree. The lane's follow-up, confirmed (re-read) | minor |
| L3-3 | `RefreshFailureClassification.java:29-33,77-78` (also `:124`), `RefreshFlow.java:87,277-278` | Javadoc says `PRE_REDEMPTION` means the presented token is "untouched and still valid / still usable". ADR-0010 now says this is only what the client holds: after a connection failure or `5xx` following redemption the server may already have rotated it. Further sites named by the leaf and not re-read: `RefreshRedemption.java:35,44`, `TokenLifecycleManager.java:82,107`, `CredentialRejectedException.java:28-31`, `ClientAuthenticationFailedException.java:46`, `doc/client/specification/token-handling.adoc:127,243`. The lane's follow-up, confirmed (re-read) | minor |
| L3-4 | `doc/adr/0010-…:187` | the Alternatives section still says "a server-error status and a connection failure mean the server never judged the credential", which the rewritten Context and Risks now retract; the ADR contradicts its own correction (re-read) | minor |
| L3-5 | `doc/adr/0005-…:55,100` | the superseded ADR still says pre-redemption leaves the token "still valid at the authorization server"; historical drift in a superseded record (re-read at line 55) | info |
| L3-6 | `doc/validation/threat-model.adoc:66` | "Token blacklisting for revoked tokens" survives (re-read by `git grep`); PLAN-19 edited the file and left it, so PLAN-17's L3-9 stays open | info |
| L3-7 | `microprofile-jwt-compatibility.adoc:155` | the explicit anchor `[[_tokensheriff_advantages_beyond_mp_jwt]]` keeps the old generated id, but nothing in the repository references it (re-read); harmless, kept | info |

**Layer 3 not shown by reading:**

- L3-N1: ADR-0026 now says an application-supplied `@Alternative TokenValidator` bypasses the startup check. `TokenValidatorStartupInitializer` resolves `Instance<TokenValidator>` (leaf, not re-read), but any later lazy resolution of the issuer-config handles by `ObservedValidatorResolver:112-113` could still run the resolvers and throw outside startup, so "not detected" may be too strong; only a run settles it.
- L3-N2: whether `xref:#id[]` with empty link text renders the section title, and whether any link breaks at build, needs an AsciiDoc and link checker; none was run (the spec's Standing Constraint is unevidenced).

**Layer 3 verdict:** findings — 3 minor (L3-1 to L3-4 counted by file group), 3 info; 2 not shown by reading

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| AGENTS.md:415 | cross-references use `xref:`, not `<< >>` | conforms | no `<<` cross-reference left (re-read by `git grep`); anchors resolve by reading, no renderer run |
| AGENTS.md | prose spelling "Token-Sheriff" | conforms | only identifiers, badge keys and one path keep the other form |
| AGENTS.md | no machine-specific paths | conforms | `AGENTS.md` carries an `https` URL instead of the `/Users/` path |
| AsciiDoc rules | blank line before lists | conforms | new and changed lists carry one; the spec's violation did not exist at base |
| CLAUDE.md / AGENTS.md | docs state a guarantee only at the scope the code gives it | deviates | L3-1 to L3-4 |
| Spec Standing Constraints | AsciiDoc link verification, zero broken links | not_shown_by_reading | no check evidenced |
| Spec Write-Boundary | edit repository source only, no `.plan/orchestrator` file | conforms | the 50-file diff has no `.plan/` path |

## Metrics and Anomalies

- Tokens: 16,357,504 (single landing message value; mixed population not stated). Per-phase outliers not supplied.
- Duration: 10,396 s wall clock (about 2.9 h).
- Effort: `unknown` — drain-completeness `complete: false`, missing key `effort`; Open Defect recorded (fourteenth producer).
- Anomalies: the landing lists `emit-landing` as `pending` and `archive-plan` as `pending` (written before either recorded), the same as PLAN-17 and PLAN-18. A finalize loop-back committed an inline fix with no change-ledger row, so the `5-execute` to `6-finalize` transition refused with `execute_commits_unrecorded` and the dispatcher appended the row by hand (candidate-lesson 009). The functional review closed with five deliverables met and one met with residuals (the lane's D6.R1: the branch net diff changes more than the Status line of the ADRs). Code review: 0 blocking, 2 advisory.

## Routing and Merge Behavior

- Review: CodeRabbit reviewed the first pushed head and raised eleven comments (ADR guarantees, the OAuth 2.1 and TLS wording, an anchor, a README class name), all fixed in `56710e6a` and `57a7a40`; its one-review-per-hour allowance was then spent, so the final head `56710e6a` has no fresh bot review and the pre-merge participation check read "participated" from the first-pass review (candidate-lesson 011). Same quota stall as PLAN-13, 14, 17 and 18.
- CI/merge: squash merge through the merge queue, commit `d1af3ebb`. The merge-queue path offers no per-enqueue message override, so the squash message may carry the PR body with CodeRabbit's summary (the PLAN-18 Watch, same cause).
- Sonar: no analysis ran for the head (the repository's change check skips the Sonar build on documentation-only PRs), so the open-issue count was undecidable and the operator accepted the gap; `sonar-roundtrip` is recorded done with `work_performed=false` (candidate-lesson 010). Read directly 2026-11-07: `main` reads 0 unresolved issues and gate OK, so nothing was hidden.
- Parallelization: PLAN-19 ran beside PLAN-17 and PLAN-18's tail. PLAN-17's three undeclared files did not collide with it. PLAN-19's own 33 undeclared files collided with nothing running, because PLAN-25 was held back.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-19 --status shipped`
- [x] row `pr` stamped — `orchestrator queue --set-row PLAN-19 --field pr --value 879`
- [x] row `landing` stamped — `orchestrator queue --set-row PLAN-19 --field landing --value landings/PLAN-19.md`
- [x] row `plan_marshall_plan_id` stamped — `orchestrator queue --set-row PLAN-19 --field plan_marshall_plan_id --value plan-19-doc-structure-hygiene`
- [x] epic.md narrative reconciled (Decision entry, Open Defect for the missing `effort`, Watch for the PLAN-19 residue)
- [x] `findings-disposition.md`: D-5, Q-12, D-7, D-10, D-11, D-12, D-15 marked FIXED (#879 `d1af3ebb`)
- [x] PLAN-25 item 7(l) added (L3-1 to L3-6, L3-N1, L3-N2) and item 7(j)(3) widened to the commons threat-model lines; Expected Surface extended in the same act
- [x] twelve inbox messages archived under `inbox/archive/plan-19-doc-structure-hygiene/`
- [x] resume anchor updated in `resume_anchor.md`
- [x] `queue-view.md` regenerated and committed with the row change

## Follow-Ups

- L3-1 to L3-4, L3-6 (documentation that contradicts the code or itself): reported to the operator at once and folded into PLAN-25 item 7(l) and 7(j)(3). None is a behaviour defect and none widens acceptance.
- L3-N1 (ADR-0026 "not detected" wording) and L3-N2 (no link check): folded into 7(l) as a run-at-outline check.
- Candidate-lessons 001 to 008 (documentation accuracy): discarded as shipped in #879; the rule they share ("a document states a guarantee only at the scope its enforcing code gives it") is already a Standing Constraint of PLAN-25. 006 (restate the opt-out next to a default, then search every document that restates the property) is the exact cause of L3-1 and is carried by 7(j)(3) and 7(l). 008 is the cause of L3-2, carried by 7(l). Candidate-lessons 009 to 011 are plan-marshall-owned and went into the seventh 2026-11-07 upstream addendum (not sent).
