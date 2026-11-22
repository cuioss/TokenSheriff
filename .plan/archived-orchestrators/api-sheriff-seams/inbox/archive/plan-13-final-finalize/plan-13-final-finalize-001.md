envelope_version=1
sender_type=plan
sender_id=plan-13-final-finalize
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-19T20:37:31Z

Candidate lesson from plan-13-final-finalize (Q-Gate 6-finalize, finding 84ab82, code-review, resolution fixed).

Finding: code_finding at BackChannelHttp.java:473-477. The documented wait bound (request timeout) holds only until the response headers arrive; a body stall after them is unbounded. The same statement stood in ADR-0031 and the COMMONS requirements. Reproduced on JDK 25.

Fix: TASK-011 (commit 89ffa3b0) reworded the wait-bound statements in BackChannelHttp, ADR-0031 and the COMMONS requirements to the headers-phase scope.

Follow-up: the first fix covered only the three named places; the next code-review round found more contradicting statements (see finding 0faedf, TASK-012).
