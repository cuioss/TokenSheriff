envelope_version=1
sender_type=plan
sender_id=plan-13-final-finalize
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-19T20:38:08Z

Candidate lesson from plan-13-final-finalize (Q-Gate 5-execute, finding 778f74, scope_creep_check, resolution accepted).

Finding: scope creep, 12 files outside the declared scope (threshold 5), including doc/client/architecture.adoc, both threat-model documents, ClientConfiguration.java and the sibling exception classes.

Resolution: accepted. The files are the earlier-accepted sibling exception classes plus the files TASK-011 and TASK-012 reworded (operator-approved wording alignment found by the code review); none comes from TASK-013.

Observation: fix tasks created by finalize loop-backs also do not extend the declared scope, so the same classes were flagged a second time.
