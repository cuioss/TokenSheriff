# WS-01: Client Seams

epic: api-sheriff-seams

> Charter document for one workstream — a coherent slice of the epic with its own goal
> and surface. Lives at `workstreams/WS-01-client-seams.md` and is tracked in the
> `workstreams[]` field of the epic header, `status.json`. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the tier contract.

## Charter

Publish, in `token-sheriff-client` and its Quarkus extension, the seams a gateway needs to stop
carrying its own copy of OIDC client plumbing: native-image registration, an authorization request
that exposes its parameters (response mode, prompt, PAR, per-call scope), public client-key
handling and config validators, a refresh-result verifier and a caching discovery resolver. The
workstream closes when the four plans have shipped in a released 0.9.10.

## Scope

- In scope: `token-sheriff-client`, `token-sheriff-client-quarkus*`, and the one cross-module edge
  PLAN-03 has into `token-sheriff-validation` (`EgressPolicy`).
- Out of scope: the validation module's token pipeline and JWKS loader (WS-02); anything in API
  Sheriff.

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-01-client-native-image-registration | staged | Register the client DSL-JSON converters and runtime-init classes in the client extension. |
| PLAN-02-client-authorization-request-seam | staged | Parameter map, `response_mode`, `prompt`, PAR-aware authorize, per-call scope. |
| PLAN-03-client-key-material-and-config-validators | staged | Public JWK export and PEM key-pair loader; public config validators; `revokeIfSupported`. |
| PLAN-04-client-refresh-verification-and-discovery-cache | staged | Public refresh-result verifier (OIDC 12.2); caching discovery resolver. |

## Sequencing and Surface Notes

- PLAN-02 and PLAN-04 both touch `RefreshFlow`; PLAN-02, -03, -04 share `token-sheriff-client`. With
  `parallelization_scope` 1 they run in the order 02, 03, 04.
- PLAN-03 also touches `EgressPolicy` in `token-sheriff-validation`, which WS-02's PLAN-05/06 may
  touch through `commons/transport`; check at outline.
