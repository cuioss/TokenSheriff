envelope_version=1
sender_type=plan
sender_id=plan-13-final-finalize
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-19T20:38:03Z

Candidate lesson from plan-13-final-finalize (Q-Gate 5-execute, finding 8245b9, scope_creep_check, resolution accepted).

Finding: scope creep, 8 files outside the declared scope (threshold 5): the 8 sibling exception classes in token-sheriff-client flow and token.

Resolution: accepted. These are the classes the operator approved adding to TASK-8 (steps 4-11) at the 4-plan quality check; comment-only edits. The decision log (927a9b) notes they were not in references affected_files.

Observation: widening a task at 4-plan did not update the declared scope that the 5-execute scope check reads, so the approved widening was flagged as creep.
