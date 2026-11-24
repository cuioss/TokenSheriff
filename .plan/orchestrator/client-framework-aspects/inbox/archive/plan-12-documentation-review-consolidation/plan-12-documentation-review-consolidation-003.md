envelope_version=1
sender_type=plan
sender_id=plan-12-documentation-review-consolidation
epic=client-framework-aspects
kind=candidate-lesson
created=2026-11-23T20:28:14Z

# Candidate lesson: a byte-identical document pins every file it links

Source signal: Q-Gate finding 5f7d3d (3-outline, false_positive_check, severity warning), resolution taken_into_account.

## Observation

D3 required ADR-0001 to stay byte-identical, yet ADR-0001 links `xref:../commons/architecture.adoc`,
`xref:../commons/threat-model.adoc` and `image::../resources/diagrams/module-architecture.svg`. D6 could merge or
delete the threat model and D9 could delete SVGs, and D11 could not repair ADR-0001 because it must not change.

## Resolution in this run

Path-stable group c3 added those three targets; D2, D6 and D9 now forbid merging or deleting them, and any
anchor ADR-0001 relies on keeps its id.

## Candidate rule

Any document frozen as byte-identical transitively freezes the paths (and anchors) of every xref/image/link
target it carries. Add those targets to the path-stable set when the freeze is declared.

Component hint: plan-marshall:phase-3-outline (doc-reduction outlines); pairs with the resources-link candidate from this plan.
