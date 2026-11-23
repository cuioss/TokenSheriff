# WS-01: Decision record for the layers above the engine

epic: client-framework-aspects

> Charter document for one workstream. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier contract.

## Charter

Decide, in one ADR (ADR-0032) backed by a sourced comparison with the Quarkus OIDC extension in web-app mode,
whether Token Sheriff ships a session module and a BFF Quarkus extension above the engine, and settle
the open questions of the addendum (§9) that the engine seams and the new modules depend on. The
workstream closes when the ADR is merged and the client docs state the new boundary.

## Scope

- In scope: `doc/adr/`, the scope statements in `doc/client/architecture.adoc`,
  `doc/client/threat-model.adoc`, `doc/client/README.adoc`, `doc/README.adoc`.
- Out of scope: any code; the requirement texts CLIENT-31 to CLIENT-35 (WS-02) and the new
  SESSION-* / BFF-* requirement sets (WS-03, WS-04), which the ADR names but does not author.

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-01-adr-session-and-bff-modules | staged | ADR-0032 + Quarkus OIDC comparison + scope edits |

## Sequencing and Surface Notes

- Gates PLAN-03 (pending-login defaults), PLAN-04 (access-token revocation at logout), and every plan of
  WS-03 and WS-04.
- Disjoint from PLAN-02, so the two may run together.
