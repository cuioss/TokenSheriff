envelope_version=1
sender_type=plan
sender_id=plan-28-epic-cleanup-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-11T11:54:15Z

# Candidate lesson: outline cited a wrong sub-package path for a read-only file

Source: Q-Gate finding 91b5b1 (phase 3-outline, warning, resolution fixed)

Deliverable 9 listed `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/jwks/HttpJwksLoader.java` as a read reference, but the class lives in the `jwks/http/` sub-package. The outline author wrote the path from memory instead of resolving it.

Resolution in run: path corrected to `jwks/http/HttpJwksLoader.java` (verified with git ls-files), outline re-validated.

Possible rule: resolve every Affected-files path (read entries included) against the file inventory before writing the outline.
