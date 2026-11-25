envelope_version=1
sender_type=plan
sender_id=plan-13-renumber-commons-validation
epic=client-framework-aspects
kind=candidate-lesson
created=2026-11-24T22:39:28Z

# Candidate lesson: integrity guards must not let 0 stand for both "absent" and "explicit zero"

Sources:
- Q-Gate 6-finalize finding 85f054 (code-review, blocking F1), fixed in commit 3ff7df48: RequirementIdIntegrityTest.findOrderViolations used `currentParent = 0` as the no-parent sentinel, so a parentless sub-id with parent number 0 passed the order guard.
- CodeRabbit PR #956 finding ed6c1d (fixed in TASK-10): an explicit `[#COMMONS-1.0]` anchor was converted to `RequirementId(1, 0)`, and `isTopLevel()` read it as top-level requirement 1, so all three integrity checks passed on an invalid anchor.

Corrective rule: in a parser or guard, represent "absent" with an explicit absence marker (Optional, nullable, or a flag), never with a value the input can also carry. Reject the explicit-zero form before constructing the value object, and give the guard a predicate test for the exact scenario it exists to catch: absent vs explicit zero, and a parentless sub-id.

Transferability: any requirement-id, version, or ordinal parser in this epic's guard tests.
