# History: API Sheriff separation seams (0.9.10)

epic: api-sheriff-seams
closed: 2026-11-22, on the operator's word ("cleanup, prepare close, close, archive and land")
final state: 16 plans shipped, 1 retired unemitted, 0 parked, 0 launched or running; inbox empty (202 messages archived); `queue-view.md` current

> Frozen record of the epic at close. Written by the `close` verb from the rendered terminal state (`resume-summary`), the queue rows and `epic.md`. `epic.md`, `settled.md`, `landings/`, `plans/`, `queue/` and `logs/` stay on disk untouched: close freezes and never deletes; the tree is the audit record. Unresolved defects and watches are carried forward below as leads, not dropped.

## Vision as pursued

API Sheriff (the gateway) must not duplicate functionality that TokenSheriff provides, and generic functionality that lives in API Sheriff but belongs in a token library must move here. The 2026-11-12 audit found that the remaining overlap was mostly a missing or private TokenSheriff seam: API Sheriff could not delete its copy until TokenSheriff published the seam. The epic shipped those seams in 0.9.10 across three modules (client, validation, the Quarkus extensions), mixing a security-sensitive change (refresh-result verification, the JWKS flood bound) with small hygiene work. "Done" was: every seam released in 0.9.10, and API Sheriff's PLAN-34 (epic `kidicap-gateway-requirements`) able to delete its code against the released version. The consumer side was never in this epic.

## Terminal queue state (rendered by `resume-summary`, verbatim)

**Phase**: orchestrating (set to `closed` by this verb)
**Inbox (derived)**: 0 queued, 202 archived
**Queue** (staged, in order):
- (empty)
- PLAN-01 (WS-01) — plan=client-native-image-registration — PR #898 — landing=landings/PLAN-01.md — status: shipped
- PLAN-02 (WS-01) — plan=client-authorization-request-seam — PR #901 — landing=landings/PLAN-02.md — status: shipped
- PLAN-03 (WS-01) — plan=plan-03-client-key-material-config-validators — PR #915 — landing=landings/PLAN-03.md — status: shipped
- PLAN-04 (WS-01) — plan=client-refresh-verification-discovery-cache — PR #907 — landing=landings/PLAN-04.md — status: shipped
- PLAN-05 (WS-02) — plan=plan-05-validation-public-seams — PR #899 — landing=landings/PLAN-05.md — status: shipped
- PLAN-06 (WS-02) — plan=jwks-loader-gateway-needs — PR #906 — landing=landings/PLAN-06.md — status: shipped
- PLAN-07 (WS-02) — plan=plan-07-public-helper-hygiene — PR #908 — landing=landings/PLAN-07.md — status: shipped
- PLAN-08 (WS-01) — plan=discovery-cache-verifier-hardening — PR #912 — landing=landings/PLAN-08.md — status: shipped
- PLAN-09 (WS-02) — plan=plan-09-public-helper-residuals — PR #916 — landing=landings/PLAN-09.md — status: shipped
- PLAN-10 (WS-01) — plan=plan-10-client-and-cache-residuals — PR #920 — landing=landings/PLAN-10.md — status: shipped
- PLAN-11 (WS-02) — plan=plan-11-final-residuals-sweep — PR #921 — landing=landings/PLAN-11.md — status: shipped
- PLAN-12 (WS-02) — plan=plan-12-open-items-sweep — PR #925 — landing=landings/PLAN-12.md — status: shipped
- PLAN-13 (WS-02) — plan=plan-13-final-finalize — PR #931 — landing=landings/PLAN-13.md — status: shipped
- PLAN-14 (WS-02) — plan=implement-plan-14-closing-sweep — PR #938 — landing=landings/PLAN-14.md — status: shipped
- PLAN-15 (WS-02) — plan=plan-15-final-fix-sweep — PR #940 — landing=landings/PLAN-15.md — status: shipped
- PLAN-16 (WS-02) — plan=plan-16-statements-and-fixture-residuals — PR #945 — landing=landings/PLAN-16.md — status: shipped
- PLAN-17 (WS-02) — status: retired

**Ordered Queue** (live queue only; every row is terminal):

| # | Plan | Workstream | Status | Surface (expected) | Lane |
|---|------|------------|--------|--------------------|------|
| — | (empty) | — | — | — | — |

(The resume anchor the summary carried at this moment is not frozen here: it described a ledger not yet landed and is superseded by the terminal anchor "epic closed — see history.md". Its content survives in the Carried-forward leads below.)

## Queue outcome per plan

- **Shipped (16):** PLAN-01 to PLAN-16. PLAN-01 to PLAN-07 were the seam plans (native-image registration; the client's authorization-request seam; key material and config validators; refresh verification and the discovery cache; the validation module's public seams; the JWKS loader's gateway needs; the public helpers), PLAN-08 to PLAN-16 the hardening and residual sweeps that each plan's landing analysis fed (each landing's three-layer analysis, from PLAN-13 on, named the next plan's content). PLAN-16 (#945, `7dfefcde`) is the last: the stalled-body statements, the fixture and test residuals, the framed names, and the dev-mode run after the `ParserConfig` fix.
- **Closed unshipped (1):** PLAN-17 (`retired`): staged on 2026-11-21 on a reading of `sonar_rest search` that listed 47 "open" Sonar issues; that verb also lists resolved ones (lesson 2026-11-21-13-004), and the project had one open issue, which PLAN-16 carried. Never emitted; its spec stays with a retirement banner.
- **Parked (0).**
- The project's open Sonar count on `main` at close: **0** (`sonar read_open_issues`, confirmed, gate OK, 0 hotspots; read 2026-11-22 after #945).

## Decision record

The decisions are in `epic.md` (`## Decisions`) and, for the settled ones, `settled.md` (every relocation pointer resolves; `compact` invariants ok at close). The standing ones: the epic cut into two workstreams by module and risk (2026-11-12); `parallelization_scope` 2; one plan per landing's findings after PLAN-07 (the operator's word each time); the hand-kept test-impl mapping (PLAN-13); the per-JDK body-phase probe (PLAN-14, and the operator's change of it at the PLAN-15 merge queue); the retirement of PLAN-17. The decision log is `logs/decision.log`.

## Unresolved defects and watches, carried forward as leads

None of these is staged; each needs the operator's word, or belongs to the marketplace or to another repository.

1. **The Playwright ten runs** of `accessibility-wcag-compliance.spec.js` and `self-devui-accessible.spec.js` (the 2026-11-15 flake entry) were never run in any plan (no Linux npm on the lanes' machine, the Windows npm refuses a UNC working directory); they need the operator's machine, or the flake entry is declared closed.
2. **"PLAN-16 landed" minors** (Open Defects in `epic.md`): the `AGENTS.md:330-333` rethrow-order sentence (the fixture rethrows the first failure, `TokenValidator` the last); framing words left in Javadoc (`RawHttpServerTest:44`, `BackChannelOriginValidatorTest:31`, and two not re-read); the `JwtTokenTamperingUtilDecodedSignatureTest` Javadoc overclaim; the accept-loop end recorded in a flag only on the `SocketException` path of `RawHttpServer`; the new boot test without a negative control; the four clients' case as a characterisation; the probe's JDK 27 branch accepting any message of a plain `IOException`.
3. **The CodeRabbit question:** its path instruction treats the hand-kept `doc/developer/test-impl-mapping.adoc` as a mirrored list; exempt it in CodeRabbit's configuration (not in this repository) or generate it.
4. **Not shown by reading:** the red proofs and one commit per rename of the late plans (squashed away), the JDK 27 class of the probe's `IOException`, and whether Quarkus loads the client jar in a child loader of the validation jar (the stated limit of `ParserConfig`).
5. **Marketplace-side findings (owner: the plan-marshall marketplace, not this repository):** the `no_sanction` commit refusal (eight plans); the `weakened_test` carry-forward ask (ten plans); the review gate that does not read the lane's replies and an unreadable CodeRabbit section; the docs-versus-executor skew (lesson 2026-11-18-12-002, seven plans); the guarded refusal for a commit with no `kind=change` row (five plans); `module_mapping_validator` (four plans); the scope-creep guard; the code-review validator; the `review_completeness` flag families; the change-ledger remap after a branch-cleanup rebase; `sonar_rest search` listing resolved issues (lesson 2026-11-21-13-004).
6. **Environment:** the installed `marshal.json` is stale and the executor is 0.1.2133 against registry pins 0.1.2139 / 0.1.2184; the steward upgrade (`marshall-steward`) was not run; `cleanup restart-check` is `indeterminate` on registry parity only (every other signal `ready`).
7. **The lessons store:** lessons named in the ledger that are not in the store (2026-11-16-06-001, 2026-11-02-14-001 with its review overdue after eleven plans of the same defect class "a statement says more than the code holds", 2026-11-11-12-002, 2026-11-11-12-003, 2026-11-15-18-002 and -004 and others).
8. **Watches that outlive the epic:** the API Sheriff compare-and-set on the refresh persist (its PLAN-33 D5, a possible TokenSheriff-side seam, trigger: API Sheriff PLAN-34); the native-image claims of PLAN-01 (trigger: the first native build of a consumer on 0.9.10); the behaviour changes API Sheriff must re-measure on bump (listed per plan in `epic.md`'s Watches and in each landing report).

## Closing rationale

Every seam the epic set out to ship is merged on `main` at `0.9.10-SNAPSHOT`: sixteen plans, each with a landing report, a three-layer analysis from PLAN-13 on, and a persisted per-claim verdict field on its spec. The open Sonar count is 0 and the quality gate is OK. What remains is not a seam: it is test and Javadoc polish, an operator-machine run, and marketplace-side tooling findings, none of which blocks API Sheriff's PLAN-34 from deleting its copies once 0.9.10 is released (the release itself is the operator's, through the repository's release workflow). The operator asked for the close; nothing is in flight and the queue is settled.
