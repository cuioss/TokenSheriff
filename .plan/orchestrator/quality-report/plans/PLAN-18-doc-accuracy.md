# PLAN-18: AsciiDoc accuracy — claims that contradict the code

epic: quality-report
workstream: WS-05

## Objective

Correct documentation claims that contradict the code at HEAD: the client "issues no HTTP of its own"
(five places — D-2); a "128 KB buffer" limit that does not exist (D-6); unmapped requirements CLIENT-23/24
(D-9); an uncaveated Quarkus version in the performance doc (D-13, already fixed, see deliverable 4); introspection described as an
implemented authenticated flow (D-14); and the undocumented last-value-wins duplicate-JSON-key semantics
(V-17d). Closes D-2, D-6, D-9, D-13, D-14, V-17d.

## Deliverables

1. **D-2** — reword to "commons owns discovery + JWKS; the client owns its back channel (`BackChannelHttp`)"
   in the five locations.
2. **D-6 / V-17d** — replace "128 KB buffer" with the real `ParserConfig` limits; document duplicate-key
   last-wins behaviour in the security reference (align with PLAN-13's response-size ceiling if landed).
3. **D-9** — map CLIENT-23 and CLIENT-24 (and CLIENT-21 as architectural) in the client README coverage rows.
4. **D-13** — DROPPED 2026-11-06 (cleanup re-grounding): already fixed. `benchmarking/doc/Analysis-03.2026-JFR-Profiling.adoc` line 12 reads "Quarkus 3.27.2 (LTS) — version at recording time; project has since upgraded", added by `4f8f3eb6` (#336, 2026-03-23); the figure 3.34.0 no longer appears under `benchmarking/doc`. Nothing to do; D-13 is closed in `findings-disposition.md` as FIXED by that commit.
5. **D-14** — mark introspection as "endpoint parsed, no flow implemented" (or remove) in the three client docs.

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
- Docs-only plan: no code change; verify each new statement against the implementing source; xref
  syntax, blank line before lists (AsciiDoc rules).

## Claim Labels

- OBSERVED: "issues no HTTP of its own" at `doc/client/README.adoc`; similar claims in `token-sheriff-client/README.adoc`, client `package-info.java`, `doc/commons/README.adoc`, `doc/README.adoc`; `BackChannelHttp` exists — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/internal/BackChannelHttp.java`
  - verdict: corroborated | checked_at: 306e6053 | by: quality-report/cleanup | rescoped: n/a | evidence: doc/client/README.adoc:19 still says it issues no HTTP of its own; BackChannelHttp exists
- OBSERVED: "128 KB" at `doc/validation/security-reference.adoc` and `doc/validation/threat-model.adoc`; no such constant in main code — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/ParserConfig.java`
  - verdict: corroborated | checked_at: 306e6053 | by: quality-report/cleanup | rescoped: n/a | evidence: security-reference.adoc:58 and threat-model.adoc:92,223 still say 128 KB buffer; real default is 256 KB
- OBSERVED: CLIENT-23/24 defined in `doc/client/requirements.adoc`, unmapped in `doc/client/README.adoc` coverage rows
  - verdict: corroborated | checked_at: 306e6053 | by: quality-report/cleanup | rescoped: n/a | evidence: requirements.adoc defines CLIENT-23/24, README rows omit 23, 24 and 21
- OBSERVED: no `IntrospectionClient*` exists; `ProviderMetadata` parses only `introspection_endpoint` — swept `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/`
  - verdict: corroborated | checked_at: 306e6053 | by: quality-report/cleanup | rescoped: n/a | evidence: no IntrospectionClient in client main; ProviderMetadata only parses introspection_endpoint
- HYPOTHESIS: the "Quarkus 3.34.0" figure was the version at recording time (March 2026) — confirm/refute at `benchmarking/doc/Analysis-03.2026-JFR-Profiling.adoc` (verify-at-outline). CONTRADICTED 2026-11-06: the document already carries the caveat and says 3.27.2; D-13 dropped and the spec re-scoped
  - verdict: contradicted | checked_at: 306e6053 | by: quality-report/cleanup | rescoped: yes | evidence: Analysis-03.2026-JFR-Profiling.adoc:12 already says Quarkus 3.27.2 version at recording time, project has since upgraded; added by 4f8f3eb6 (#336); D-13 dropped

## Expected Surface

- OBSERVED: `doc/client/README.adoc`
- OBSERVED: `token-sheriff-client/README.adoc`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/package-info.java`
- OBSERVED: `doc/commons/README.adoc`
- OBSERVED: `doc/README.adoc`
- OBSERVED: `doc/validation/security-reference.adoc`
- OBSERVED: `doc/validation/threat-model.adoc`
- OBSERVED: `doc/client/threat-model.adoc`
- OBSERVED: `doc/client/requirements.adoc`
- OBSERVED: `doc/client/specification/retrieval-flow.adoc`
- OBSERVED: `token-sheriff-quarkus-parent/doc/performance/jwt-validation-performance.adoc`

## Dependencies and Sequencing

- Depends on: none (PLAN-13 preferred first for the response-size wording)
- Overlaps with: PLAN-19 (`doc/README.adoc`, `doc/client/*.adoc`, `doc/commons/README.adoc` via D-11 xref sweep) — PLAN-19 runs after
- Adjacent to: `doc/client/architecture.adoc` — already correct, reference only

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/quality-report/plans/PLAN-18-doc-accuracy.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
