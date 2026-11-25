envelope_version=1
sender_type=plan
sender_id=plan-13-renumber-commons-validation
epic=client-framework-aspects
kind=candidate-lesson
created=2026-11-24T22:39:39Z

# Candidate lesson: reference-integrity guards must validate range endpoints and resolve the repository root from either invocation directory

Sources: CodeRabbit PR #956 findings 61bb85 and 6ccba2, both fixed in TASK-10 (commit 3ff7df48) on RequirementIdIntegrityTest.

Observations:
- Range references such as `COMMONS-1..99` were parsed as `COMMONS-1` only, so a dangling range end id passed the "no dangling reference" check.
- The repository-root resolver always moved to the parent of the chosen directory. When `basedir` was absent and `user.dir` was already the repository root, it left the repository, and the sweep then failed its setup assertions instead of checking anything.
- Not fixed, deferred as follow-up: xref targets (`validation/requirements.adoc#COMMONS-11`) are not checked against the target document's anchors. The token resolves in the commons catalog even though the linked document has no such anchor.

Corrective rule: a reference guard must resolve every id a reference denotes, including both ends of a range. A test that locates the repository must search the start directory and its ancestors for repository markers, so it works from both the module root and the repository root.

Transferability: any doc-traceability or id-integrity guard test in the epic. The open xref-target gap is a possible follow-up item.
