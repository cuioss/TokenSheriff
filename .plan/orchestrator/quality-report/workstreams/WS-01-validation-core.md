# WS-01: Validation core hardening

epic: quality-report

> Charter document for one workstream. Tracked in the `workstreams[]` field of `status.json`.

## Charter

Close every still-valid finding in `token-sheriff-validation` production code (V-1…V-17 except
fixed V-6) and the validation-module test gaps (TV-2, TV-4, TV-6, TV-7, validation gap list).
Closed when each V/TV row in `findings-disposition.md` owned by this workstream has shipped.

## Scope

- In scope: `token-sheriff-validation/src/main/**`, `token-sheriff-validation/src/test/**`,
  `doc/LogMessages.adoc` (log-identifier rows touched by PLAN-13).
- Out of scope: Javadoc-only example fixes (WS-05 / PLAN-17), client module (WS-02), Quarkus (WS-03).

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-01-jwks-loader-lifecycle | staged | V-3, V-4, V-5, V-8, V-16 |
| PLAN-03-cache-hit-policy | staged | V-2, V-9 |
| PLAN-05-jwe-ecdh-es | staged | V-1, V-17g, gap VAL 8 |
| PLAN-07-jwks-staleness-timeouts | staged | V-7 remainder, V-10 |
| PLAN-09-dpop-parser-hardening | staged | V-11, V-12, V-17b, V-17c, V-17e |
| PLAN-11-audience-semantics | staged | V-17f, TV-2, gap VAL 4 |
| PLAN-13-log-transport-hygiene | staged | V-13, V-14/D-4, V-15, V-17a, V-17h, D-3 |
| PLAN-15-validation-test-gaps | staged | TV-4, TV-6, TV-7, gaps VAL 2/3/5/6 |

## Sequencing and Surface Notes

- `HttpJwksLoader.java` is shared by PLAN-01 → PLAN-07 → PLAN-13; strictly sequential in that order.
- `NonValidatingJwtParser.java` / `JwtHeader.java`: PLAN-05 before PLAN-09.
- `TokenValidator.java`: PLAN-01 (close) and PLAN-03 (skew) — single-file overlap, allowed concurrently.
- `ExpirationValidatorTest.java`: PLAN-03, PLAN-09, PLAN-15 — keep sequential.
