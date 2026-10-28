# PLAN-19: Documentation structure and hygiene

epic: quality-report
workstream: WS-05

## Objective

Bring the documentation entry points and conventions in order: `AGENTS.md` residue (machine-specific path,
missing `token-sheriff-client` module, `README.adoc` vs `README.md`, incomplete skills list — D-5/Q-12);
the hub lists 1 of 15 ADRs and ADR-0002 is still "Proposed" (D-7); hub omissions and an orphaned
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
- OBSERVED: `doc/adr/` holds ADR-0001…0015; `doc/README.adoc` lists only ADR-0001; ADR-0002 status "Proposed" — read at `doc/README.adoc`
- OBSERVED (derived count): 66 `<<…>>` references across the 12 files listed below — counted with `git grep` at `aa77acd9`
- OBSERVED: "cui-http-client" appears in `doc/validation/architecture.adoc`, `doc/validation/security-reference.adoc`, `doc/validation/threat-model.adoc`
- OBSERVED: no README in `token-sheriff-quarkus-parent/token-sheriff-client-quarkus/`

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
