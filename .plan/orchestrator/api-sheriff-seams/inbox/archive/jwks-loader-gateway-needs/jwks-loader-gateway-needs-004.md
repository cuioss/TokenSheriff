envelope_version=1
sender_type=plan
sender_id=jwks-loader-gateway-needs
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-15T11:00:28Z

# Candidate lesson: hand-kept HTTP-only property list mirrored JwtPropertyKeys.ISSUERS

Source signal: review-bot finding 29c7fa (coderabbit, PR #906, inline on IssuerConfigResolver.java:533), resolution fixed by TASK-14 (list defined once beside the key constants) plus a derivation guard test in TASK-15 (commit 6f3e8e2a).

## What happened

`IssuerConfigResolver.validateNoOrphanedHttpProperties` kept its own list of the HTTP-only keys. That list had to mirror the keys declared in `JwtPropertyKeys.ISSUERS`. The two new keys added by this plan had to be added in both places. A key added only to the constants would be ignored without warning by non-HTTP issuers. The same defect class came back in the next review round for the DPoP sub-property list (finding b1550e, TASK-17). The repository's `.coderabbit.yaml` path instructions already flag this pattern ("a hardcoded list that must mirror a set defined elsewhere is a defect unless derived").

## Candidate rule

When a plan adds a configuration key, check every validation or membership list that enumerates sibling keys. Derive the list from the key definitions, or add a reflection-based completeness guard test. Do this for all such lists in the module at once, not only the one being edited.

Component hint: token-sheriff-validation-quarkus config (IssuerConfigResolver, JwtPropertyKeys).
