envelope_version=1
sender_type=plan
sender_id=plan-13-final-finalize
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-19T20:37:36Z

Candidate lesson from plan-13-final-finalize (Q-Gate 6-finalize, finding 0faedf, code-review, resolution fixed).

Finding: code_finding at doc/commons/requirements.adoc:158-160. After TASK-011 corrected the body-timeout limit, it contradicted COMMONS-4, the T-DOS threat-model row, transport.adoc and the ClientConfiguration.readTimeoutSeconds Javadoc, which still promised a bounded wait.

Fix: TASK-012 (commit a9f97d16) aligned COMMONS-4, the commons threat-model T-DOS row, transport.adoc, ClientConfiguration.readTimeoutSeconds and every sibling found by a repository sweep to the headers-phase scope.

Observation: the wording correction took two code-review loop-backs (iterations 1/5 and 2/5) because the first fix (TASK-011) did not sweep the repository for the same claim. A third round still left an advisory on the ClientConfiguration.readTimeoutSeconds Javadoc (decision log 7145b5).
