# WS-02: Validation and Shared-Helper Seams

epic: api-sheriff-seams

> Charter document for one workstream — a coherent slice of the epic with its own goal
> and surface. Lives at `workstreams/WS-02-validation-seams.md` and is tracked in the
> `workstreams[]` field of the epic header, `status.json`. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier contract.

## Charter

Publish, in `token-sheriff-validation` and the validation Quarkus extension, the seams a gateway
needs so it can validate tokens without re-assembling TokenSheriff-internal classes: a public
signature-only verification, native-JSON claims, issuer-config and loader-status exposure for
externally built validators, an opt-in JWKS unknown-key-id refresh and retry backoff, and a few
shared helpers (RFC 6750 challenge building, text sanitising, bounded body reading). The workstream
closes when the three plans have shipped in a released 0.9.10, or the operator has declared an
item out of scope.

## Scope

- In scope: `token-sheriff-validation` (pipeline, JWKS loaders, `commons/transport`,
  `commons/error`), `token-sheriff-validation-quarkus` (health, observability, producers).
- Out of scope: the OIDC client (WS-01); anything in API Sheriff.

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-05-validation-public-seams | staged | `verifySignature`, raw claims, issuer-config exposure; logout-token validator gated on an operator decision. |
| PLAN-06-jwks-loader-gateway-needs | staged | Opt-in unknown-kid refresh window and initial retry backoff; flood-bounded. |
| PLAN-07-public-helper-hygiene | staged | Challenge builder / Bearer extractor, sanitiser escape mode, one bounded body handler. Low priority. |

## Sequencing and Surface Notes

- PLAN-05 and PLAN-06 both touch `token-sheriff-validation`; PLAN-06 is isolated to the JWKS loader
  and its config. They run in the order 05, 06, 07 at `parallelization_scope` 1.
- PLAN-07 touches `ErrorTextSanitizer` and `BoundedBodyHandlers`, which WS-01 PLAN-03 may also
  touch through `EgressPolicy`'s neighbours in `commons/transport`; check at outline.
