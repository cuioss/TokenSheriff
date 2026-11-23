# WS-04: `token-sheriff-bff-quarkus` extension

epic: client-framework-aspects

> Charter document for one workstream. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier contract.

## Charter

Ship the browser-facing layer as a Quarkus extension (runtime + deployment pair) on top of
`token-sheriff-session`: endpoints, cookies, flow defaults, CSRF, the Quarkus `SecurityIdentity`,
application hooks, limits and answers, and boot-validated configuration under `sheriff.bff.*`. Audit
Notary is the intended first consumer. API Sheriff has no example of this layer to extract from, because
the gateway runs its own pipeline, so the layer is designed against Audit Notary's shipped code and its
specification. The workstream closes when the extension is released, covered by
integration tests against Keycloak, and proven in native mode.

## Scope

- In scope: `token-sheriff-quarkus-parent/token-sheriff-bff-quarkus/` and `-deployment/`, the
  quarkus-parent and bom poms, `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/`, a
  new `doc/bff/` documentation set.
- Out of scope: the session lifecycle itself (WS-03). Also out of scope, staying in the application
  (addendum §7): domain authorization, canonical addresses, the DOMAIN members of the session view
  (added through `SessionViewContributor`), and the first-login gate. The generic session-info view and
  logged-in sentinel (BFF-9) is IN scope (operator observation of 2026-11-23; split per ADR-0032
  question 11).

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-08-bff-extension-core | staged | scaffold, BFF-1, BFF-2, BFF-3, BFF-8, BFF-9 (session-info sentinel) |
| PLAN-09-bff-csrf-identity-and-hooks | staged | BFF-4..BFF-7 |
| PLAN-10-bff-integration-native-and-docs | staged | Keycloak ITs, native, user docs |

## Sequencing and Surface Notes

- Depends on PLAN-01 (ADR) and on WS-03 landing. Strictly sequential within the workstream.
- ⛔ Keep `quarkus-bom` before `token-sheriff-bom` in `token-sheriff-quarkus-parent/pom.xml`. The order is
  load-bearing.
