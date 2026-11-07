envelope_version=1
sender_type=plan
sender_id=plan-24-client-id-strict-typing
epic=quality-report
kind=candidate-lesson
created=2026-11-06T16:56:22Z

# Candidate lesson: strict claim mappers cannot reject explicit JSON `null` - `MapRepresentation.fromJson` drops null entries

**Source signal:** automated review (CodeRabbit inline comment on PR #876, finding `7a33e9`, accepted as pre-existing)
**File:** `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/domain/claim/ClaimName.java:179`

## What happened

The plan mapped `client_id` through `StrictStringMapper` so non-string JSON values are rejected. CodeRabbit pointed out
that a token with `"client_id": null` plus a matching string `azp` still builds and is accepted via the `azp` branch,
because `MapRepresentation.fromJson` removes null-valued entries before `TokenBuilder` maps any claim. The claim is
simply absent by the time a mapper runs. The comment was accepted as out of scope: the behaviour is identical on main,
and preserving null entries would change behaviour for every claim.

## Reusable fact / follow-up (suggested)

- Any "strict typing" change to a claim mapper in token-sheriff-validation covers non-null wrong types only; explicit
  JSON `null` is indistinguishable from an absent claim at mapper level. Docs and tests that claim a value "is
  rejected" should say "non-string values" and treat null as absence.
- Possible follow-up plan: decide whether explicit `null` for identifier claims (`iss`, `sub`, `jti`, `client_id`,
  `azp`) should be rejected, which needs `MapRepresentation` to keep null-valued keys (a cross-claim behaviour change).

## Suggested component / shape

KNOWLEDGE-shaped (project fact), owner module `token-sheriff-validation`; candidate for an architecture `tip` hint or
an epic follow-up item rather than a defect lesson.
