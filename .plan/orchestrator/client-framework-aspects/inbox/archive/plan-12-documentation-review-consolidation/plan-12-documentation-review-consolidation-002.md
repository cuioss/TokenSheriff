envelope_version=1
sender_type=plan
sender_id=plan-12-documentation-review-consolidation
epic=client-framework-aspects
kind=candidate-lesson
created=2026-11-23T20:28:05Z

# Candidate lesson: a "keep unchanged" ADR must not cite an ADR the plan deletes

Source signal: Q-Gate finding 20deeb (3-outline, false_positive_check, severity warning), resolution taken_into_account.

## Observation

D3 left "keep" ADRs untouched and dropped them from the mutation set, while D11 fixed references only inside
D3-D9 mutation sets. ADRs cross-cite heavily (30 of 31 ADR files cite another ADR; ADR-0006 cited the
to-be-deleted ADR-0005), so a kept ADR citing a deleted one would keep a dangling reference and fail D11's
zero-hit criterion.

## Resolution in this run

A kept-ADR citation rule was added to D2: a keep ADR may not cite a delete ADR; each such pair is resolved by
condensing either the citing ADR (citation retargeted) or the cited one. ADR-0005 stayed delete, so every ADR
citing it was condensed.

## Candidate rule

Before classifying ADRs as keep/condense/delete, compute the ADR citation graph and enforce closure: no
untouched record may reference a record the same plan deletes. Classification and reference repair must be
planned together.

Component hint: plan-marshall:manage-adr or plan-marshall:phase-3-outline (ADR-reduction outlines).
