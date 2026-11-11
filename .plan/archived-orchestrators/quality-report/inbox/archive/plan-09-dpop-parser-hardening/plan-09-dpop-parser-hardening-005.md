envelope_version=1
sender_type=plan
sender_id=plan-09-dpop-parser-hardening
epic=quality-report
kind=candidate-lesson
created=2026-11-04T12:33:18Z

# Candidate lesson: the DPoP proof path must enforce the same key-binding checks as the access-token path

**Source plan:** plan-09-dpop-parser-hardening (PR #851)
**Signal:** review-bot finding b6a5a2 item 1 (coderabbit follow-up, Minor / Functional Correctness, fixed by TASK-17)
**Suggested component:** pm-dev-java:java-security / project security convention for `DpopProofValidator`

## What happened

For JWKS keys, the access-token path binds the JWS `alg` to the EC curve: `KeyProcessor` and `TokenSignatureValidator` require ES256 with P-256, ES384 with P-384 and ES512 with P-521. The DPoP proof path only checked that `alg` was a supported algorithm and never compared it with the `crv` of the JWK embedded in the proof. An ES384 proof with a P-256 key therefore reached signature verification. TASK-17 added the alg/curve check to `DpopProofValidator`. It rejects with `DPOP_PROOF_INVALID` and a message that names the alg/curve mismatch, separate from the range-check message.

## Reusable rule

The DPoP proof carries its own key (the `jwk` header), so every key-sanity check the token path applies to a JWKS key must also be applied to the embedded proof key: alg/curve binding, key type versus alg family, minimum key size, and the ECDSA range check. When hardening one signature path, check the other path for the same check, and add a wired DPoP test for each mismatch class. Where possible, route both paths through one shared check so they cannot drift apart again.

## Why it matters

A self-supplied key is the less trusted of the two inputs. A check that exists on the JWKS path but is missing on the DPoP path is a parity gap an attacker can choose to use.
