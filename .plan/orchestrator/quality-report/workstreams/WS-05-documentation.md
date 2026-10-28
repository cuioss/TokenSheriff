# WS-05: Documentation accuracy

epic: quality-report

> Charter document for one workstream. Tracked in the `workstreams[]` field of `status.json`.

## Charter

Bring Javadoc usage examples and the AsciiDoc set back in line with the code at HEAD: every
example compiles and runs as written, every claim about behaviour matches the implementation,
and the documentation hub indexes what exists.

## Scope

- In scope: Javadoc in production classes (J-1…J-7, J-9…J-12), `doc/**`, `AGENTS.md`, module
  READMEs, `benchmarking/benchmarking-common/doc/LogMessages.adoc`.
- Out of scope: doc changes that move together with a code fix (D-1 → PLAN-04, D-3/D-4 → PLAN-13,
  D-8/J-8 → PLAN-08).

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-17-javadoc-examples | staged | J-1…J-7, J-9…J-12 |
| PLAN-18-doc-accuracy | staged | D-2, D-6, D-9, D-13, D-14, V-17d |
| PLAN-19-doc-structure-hygiene | staged | D-5/Q-12, D-7, D-10, D-11, D-12, D-15 |

## Sequencing and Surface Notes

- PLAN-17 edits Javadoc in `IssuerConfig`, `TokenValidator`, `HttpJwksLoader` package-info,
  `JwksLoaderFactory`, `NonValidatingJwtParser` — run it AFTER PLAN-01/05/07 land so examples are
  written against the final APIs.
- PLAN-18 and PLAN-19 both touch `doc/README.adoc` (D-2 line vs ADR index / hub entries) —
  single-file overlap, but sequence them anyway to avoid a rebase on the hub.
