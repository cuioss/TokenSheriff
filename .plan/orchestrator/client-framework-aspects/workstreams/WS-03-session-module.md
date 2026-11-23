# WS-03: `token-sheriff-session` module

epic: client-framework-aspects

> Charter document for one workstream. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier contract.

## Charter

Ship a framework-neutral, pure-Java module with the server-side session lifecycle: record and store,
deadlines, create-versus-update, login-ends-session, refresh coordination, logout and termination, a
session-ended notification, step-up into the same session, and a binding seam. It is EXTRACTED where API
Sheriff's `bff/session`, `bff/pending`, `bff/logout` and the generic half of `bff/refresh` already hold
hardened code. It is DESIGNED where no consumer has an example: tokens held through
`TokenLifecycleManager`, the end-cause enum, and the pending action. The workstream closes when the module
is released with its own requirement set and threat-model entries.

## Scope

- In scope: the new `token-sheriff-session/` module, the root `pom.xml` and `bom/pom.xml` entries, a new
  `doc/session/` documentation set, `doc/developer/test-impl-mapping.adoc` rows.
- Out of scope: HTTP, cookies' wire format beyond the handle, CSRF, Quarkus (WS-04); any change inside
  API Sheriff or Audit Notary (hand-off notes in `epic.md`).

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-05-session-module-core | staged | scaffold, SESSION-1..3 |
| PLAN-06-session-lifecycle-and-termination | staged | SESSION-4..7 |
| PLAN-07-session-step-up-and-binding-seam | staged | SESSION-8, SESSION-9 |

## Sequencing and Surface Notes

- All three depend on PLAN-01. Their specs are re-grounded against the accepted ADR before emission.
- Strictly sequential: the new module directory is unresolved for the surface reader until it exists,
  so the gate cannot see overlaps inside it.
