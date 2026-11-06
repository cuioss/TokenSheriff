# PLAN-19: Documentation structure and hygiene

epic: quality-report
workstream: WS-05

## Objective

Bring the documentation entry points and conventions in order: `AGENTS.md` residue (machine-specific path,
missing `token-sheriff-client` module, `README.adoc` vs `README.md`, incomplete skills list — D-5/Q-12);
the hub lists 1 of 26 ADRs (15 when staged; PLAN-22 added 0020 to 0026) and ADR-0002 is still "Proposed" (D-7); hub omissions and an orphaned
benchmarking LogMessages file (D-10); 66 `<<…>>` cross-references in 12 files plus a missing blank line
(D-11); "TokenSheriff" vs "Token-Sheriff" prose spelling (D-12); missing client-quarkus README and
"cui-http-client" naming (D-15). Closes D-5, Q-12, D-7, D-10, D-11, D-12, D-15.

## Deliverables

1. **D-5 / Q-12** — `AGENTS.md`: remove the machine path, add `token-sheriff-client/`, fix `README.md`,
   list all `.claude/skills/` entries.
2. **D-7 / D-10** — `doc/README.adoc` gains a full ADR index and the `cve-lessons-register` entry; ADR-0002
   status updated to match reality (confirm with the operator if uncertain); benchmarking-common
   LogMessages retitled and linked.
3. **D-11** — convert `<<…>>` to `xref:` in the 12 files; fix the list-after-paragraph blank line in
   `doc/client/requirements.adoc`.
4. **D-12** — prose rename to "Token-Sheriff" (code identifiers untouched).
5. **D-15** — README for `token-sheriff-client-quarkus` (short, links config doc); "cui-http-client" →
   "cui-http" in the validation docs.

## Standing Constraints

- Every claim below was re-grounded at HEAD `aa77acd9`; re-verify each against the implementing
  source at outline before scoping on it (refutation loops back to re-scope). The original report
  text is in `.plan/orchestrator/quality-report/source-report/` (read-only, stale line numbers).
- The PR title/body references every finding ID it closes (e.g. "Fix V-3"). Do NOT edit
  `source-report/` or `findings-disposition.md` — the orchestrator records dispositions.
- Fail-closed posture is non-negotiable: no fix may widen acceptance of a token, a callback or a
  configuration that is rejected today, unless the deliverable says so explicitly.
- Every behavioural fix ships with a test that fails before and passes after (JUnit 5, CUI test
  conventions, generators where variation matters); build and verify via Maven only.
- Log changes follow CUI logging: LogRecord in the module's LogMessages class, and
  `doc/LogMessages.adoc` updated in the same PR for any new/renumbered identifier.
- SonarCloud analyses PRs (`sonar: configured`): an open Sonar issue is fixed in a pushed commit,
  or dismissed with a stated reason recorded as `{issue-key}: {reason}` in the run's progress
  record and transmitted via `plan-marshall:workflow-integration-sonar:sonar_rest transition
  --issue-key {key} --transition {falsepositive|wontfix}`. A dismissal the run cannot transmit is
  a blocker. The merge gate reads the open-issue count at the head — `0` confirmed or every
  remaining issue dismissed; an `undecidable` read blocks.
- Docs-only plan; run AsciiDoc link verification after the xref sweep (zero broken links, as at review time).

## Claim Labels

- OBSERVED: `AGENTS.md` still carries `/Users/oliver/git/cui-llm-rules/...`, omits `token-sheriff-client/`, names `README.adoc`, lists one skill — read at `AGENTS.md`
  - verdict: corroborated | checked_at: 306e6053 | by: quality-report/cleanup | rescoped: n/a | evidence: AGENTS.md:374 still has /Users/oliver path; omits token-sheriff-client; says README.adoc; lists one skill
- OBSERVED: `doc/adr/` holds ADR-0001…0015; `doc/README.adoc` lists only ADR-0001; ADR-0002 status "Proposed" — read at `doc/README.adoc`. CONTRADICTED 2026-11-06 in the count and re-scoped: `doc/adr/` now holds ADR-0001…0026 (PLAN-22 added 0020 to 0026); the hub still lists only ADR-0001 (line 14) and ADR-0002 is still `Proposed` (line 20)
  - verdict: contradicted | checked_at: 306e6053 | by: quality-report/cleanup | rescoped: yes | evidence: doc/adr now holds 0001 to 0026; hub lists only ADR-0001 (line 14); ADR-0002 still Proposed (line 20); spec re-scoped
- OBSERVED (derived count): 66 `<<…>>` references across the 12 files listed below — counted with `git grep` at `aa77acd9`. CONTRADICTED 2026-11-06 in the count and re-scoped: `git grep` at `306e6053` finds 68 across the same 12 files (requirements 25, retrieval-flow 7, test-strategy 7, commons/requirements 7, token-handling 6, threat-model 4, others 1 to 3); all 12 still contain `<<`, so the scope holds and the number is 68
  - verdict: contradicted | checked_at: 306e6053 | by: quality-report/cleanup | rescoped: yes | evidence: git grep at 306e6053 finds 68 << refs across the same 12 files, not 66; spec re-scoped to 68
- OBSERVED: "cui-http-client" appears in `doc/validation/architecture.adoc`, `doc/validation/security-reference.adoc`, `doc/validation/threat-model.adoc`
  - verdict: corroborated | checked_at: 306e6053 | by: quality-report/cleanup | rescoped: n/a | evidence: cui-http-client still in architecture.adoc:89, security-reference.adoc:51, threat-model.adoc:45,189
- OBSERVED: no README in `token-sheriff-quarkus-parent/token-sheriff-client-quarkus/`
  - verdict: corroborated | checked_at: 306e6053 | by: quality-report/cleanup | rescoped: n/a | evidence: client-quarkus module holds only pom.xml and src, no README
- OBSERVED: the ADR claim above is stale. PLAN-22 (#863, merge `38aefc0a`) added ADR-0020 to ADR-0026 (the tree now holds 0001 to 0026) and amended ADR-0005, ADR-0010 and ADR-0017; `doc/README.adoc` was not touched — read with `git show --stat` and `ls doc/adr`. No ADR file contains the word "provisional". `AGENTS.md` also changed in PLAN-22 (+60/-: the two PLAN-20 advisories). Re-read `doc/adr/`, `doc/README.adoc` and `AGENTS.md` at outline; the index covers the whole of 0001 to 0026
  - verdict: corroborated | checked_at: 306e6053 | by: quality-report/cleanup | rescoped: n/a | evidence: 38aefc0a is an ancestor of origin/main; added ADR 0020 to 0026, changed 0005, 0010, 0017, not doc/README.adoc; no provisional marker; AGENTS.md +44/-16

## Expected Surface

- OBSERVED: `AGENTS.md`
- OBSERVED: `doc/README.adoc`
- OBSERVED: `doc/adr/0002-Observability_beans_observe_the_resolved_TokenValidator_not_the_issuer_property_namespace.adoc`
- OBSERVED: `benchmarking/benchmarking-common/doc/LogMessages.adoc`
- OBSERVED: `doc/client/README.adoc`
- OBSERVED: `doc/client/architecture.adoc`
- OBSERVED: `doc/client/requirements.adoc`
- OBSERVED: `doc/client/specification/retrieval-flow.adoc`
- OBSERVED: `doc/client/specification/test-strategy.adoc`
- OBSERVED: `doc/client/specification/token-handling.adoc`
- OBSERVED: `doc/client/threat-model.adoc`
- OBSERVED: `doc/commons/README.adoc`
- OBSERVED: `doc/commons/requirements.adoc`
- OBSERVED: `doc/validation/README.adoc`
- OBSERVED: `doc/validation/cve-lessons-register.adoc`
- OBSERVED: `doc/validation/specification/multi-idp-testing.adoc`
- OBSERVED: `doc/validation/requirements.adoc`
- OBSERVED: `doc/validation/specification/microprofile-jwt-compatibility.adoc`
- OBSERVED: `doc/validation/architecture.adoc`
- OBSERVED: `doc/validation/security-reference.adoc`
- OBSERVED: `doc/validation/threat-model.adoc`
- HYPOTHESIS: new `token-sheriff-quarkus-parent/token-sheriff-client-quarkus/README.adoc` (verify-at-outline)

## Dependencies and Sequencing

- Depends on: PLAN-18 (shared doc files)
- Overlaps with: PLAN-18 (multi-file — strictly sequential)
- Adjacent to: `doc/LogMessages.adoc` — PLAN-13

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/quality-report/plans/PLAN-19-doc-structure-hygiene.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
