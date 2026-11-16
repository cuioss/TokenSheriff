envelope_version=1
sender_type=plan
sender_id=client-authorization-request-seam
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-13T13:58:26Z

component=plan-marshall:phase-6-finalize
category=anti-pattern

# Functional-review verdict criterion ids must follow the frozen criteria snapshot, not the renumbered outline

## Observation

After an operator-authorized removal of a criterion from deliverable D2, the current solution outline renumbered the remaining criteria. The functional-review verdict graded a criterion as `D2.C5` using the outline's new position, while the frozen criteria snapshot still held it as `D2.C6`. Result: the snapshot's `D2.C6` was left ungraded and an open Done-when finding was raised against a criterion that had in fact been satisfied.

## Proposed rule

Functional-review verdict ids must be keyed by the frozen criteria snapshot's positions (the reviewed contract), never by the current outline's positions. When a criterion is removed by operator authorization, the snapshot entry should be marked removed/waived rather than the outline renumbering silently shifting ids; the reviewer should map by snapshot id (or by criterion text) and not by ordinal.
