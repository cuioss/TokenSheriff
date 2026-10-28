# WS-03: Quarkus extension correctness

epic: quality-report

> Charter document for one workstream. Tracked in the `workstreams[]` field of `status.json`.

## Charter

Make the Quarkus extensions behave as documented: fail at startup on invalid config, keep
liveness honest, register all providers, honour class-level `@BearerAuth`, allow bean overrides,
fail loud on JWE misconfig — and make the Keycloak integration tests assert what they claim.
Closed when Q-1…Q-6, Q-8, Q-9, D-1 remainder, D-8/J-8 and TC-1/3/4/8 + IT gaps have shipped.

## Scope

- In scope: `token-sheriff-quarkus-parent/**` (runtime, deployment, integration-tests),
  `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/README.adoc`.
- Out of scope: Javadoc example fixes J-5/J-10 (PLAN-17), benchmarking (WS-04).

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-04-quarkus-startup-health | staged | Q-2 rem., Q-3, Q-4, Q-8, D-1 rem. |
| PLAN-08-quarkus-config-cdi | staged | Q-1, Q-5, Q-6, Q-9, D-8/J-8 |
| PLAN-16-keycloak-it-assertions | staged | TC-1 rem., TC-3, TC-4, TC-8, IT gap 10 |

## Sequencing and Surface Notes

- PLAN-04 and PLAN-08 share `IssuerConfigResolver.java` only as a Javadoc touch (Q-3 claim text in
  PLAN-04) vs D-8 code path in PLAN-08 — single file.
- PLAN-16 is IT-cost (Keycloak container, `-Pintegration-tests`); run it alone or beside a
  docs-only plan.
