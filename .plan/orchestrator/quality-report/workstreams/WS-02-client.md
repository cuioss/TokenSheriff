# WS-02: OAuth client hardening

epic: quality-report

> Charter document for one workstream. Tracked in the `workstreams[]` field of `status.json`.

## Charter

Close the still-valid `token-sheriff-client` findings (C-1…C-8) and client unit-test gaps
(TC-5, TC-6, TC-7, TC-9 hygiene, client gap list 7/8/9). Closed when every C/TC row owned here
has shipped.

## Scope

- In scope: `token-sheriff-client/src/main/**`, `token-sheriff-client/src/test/**`; the shared
  `InboundErrorNormalizer` sanitizer in validation (C-8a only).
- Out of scope: Keycloak ITs (WS-03 / PLAN-16), client docs (WS-05).

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-02-refresh-family-rotation | staged | C-1, C-2, C-8b, TC-6 |
| PLAN-06-callback-logout-hardening | staged | C-4, C-5, C-6, C-7/TC-5, C-8a |
| PLAN-10-dpop-binding-stepup | staged | C-3, C-8c, C-8d |
| PLAN-14-client-adversarial-tests | staged | TC-7, TC-9 hygiene, client gaps 7/8/9 |

## Sequencing and Surface Notes

- PLAN-02 and PLAN-06 are disjoint (lifecycle/token vs flow/logout/internal).
- PLAN-06 and PLAN-10 both touch `flow/` but different files; `AuthorizationRequestBuilder.java` is
  shared (C-4 helper extraction vs C-8c PAR) — single file.
- PLAN-14 runs last in the workstream: it adds tests around `TokenEndpointClient`, `IssValidator`
  and `EndSessionFlow` whose behaviour PLAN-06/PLAN-10 change.
