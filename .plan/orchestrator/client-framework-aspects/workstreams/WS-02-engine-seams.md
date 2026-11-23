# WS-02: Engine seams CLIENT-31 to CLIENT-35

epic: client-framework-aspects

> Charter document for one workstream. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier contract.

## Charter

Publish the protocol-level building blocks that both BFF consumers re-implemented, in the published-seam
style of CLIENT-27 to CLIENT-30, inside `token-sheriff-client` (and, where the existing type lives there,
`token-sheriff-validation`). None of them involves a cookie or a session store. The workstream closes
when CLIENT-31 to CLIENT-35 are implemented, documented, tested and released.

## Scope

- In scope:
  - `token-sheriff-client`: the `flow`, `lifecycle`, `logout` and `internal` packages, plus a new
    pending-authorization package.
  - `token-sheriff-validation`: the `logout` package and `IdTokenContent`.
  - `doc/client/` and `doc/validation/` requirements and specification.
- Out of scope: session records, cookies, HTTP endpoints, Quarkus wiring (WS-03, WS-04). The addendum's
  §8.1 native registration is out: it shipped in 0.9.10 as CLIENT-26.

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-02-step-up-binding-and-backchannel-selector | staged | CLIENT-34, CLIENT-35 (narrowed to the selector), ID-token groups accessor |
| PLAN-03-pending-authorization-store | staged | CLIENT-31 |
| PLAN-04-refresh-disposition-and-logout-step | staged | CLIENT-32, CLIENT-33 |
| PLAN-11-resource-indicators | staged | RFC 8707 `resource` on every authorization/token request (next free CLIENT id); ADR-independent |

## Sequencing and Surface Notes

- PLAN-02 runs independently of the ADR. PLAN-03 and PLAN-04 wait for PLAN-01.
- All three share `doc/client/requirements.adoc` and probably `internal/ClientLogMessages.java` /
  `doc/LogMessages.adoc`. Two or more shared files trigger the operator overlap question at `next`. The
  expected answer is to run them one after another.
