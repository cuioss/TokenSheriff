envelope_version=1
sender_type=plan
sender_id=plan-19-doc-structure-hygiene
epic=quality-report
kind=candidate-lesson
created=2026-11-06T22:43:02Z

# Candidate lesson: ADR claimed identity-binding coverage the code does not give

- Source: CodeRabbit inline finding on PR #879 (thread PRRT_kwDOOA6qMM60tORL), finding hash 69773a, resolution fixed (fix task TASK-7, commit 56710e6)
- File: doc/adr/0007-The_refresh_path_binds_identity_against_a_retained_session_subject_not_a_re-parsed_stored_ID_token.adoc:15
- Signal: automated review (review-bot finding remediated in run)

## What slipped

The ADR stated that every refresh binds identity against the retained session subject and implied that a session stored without a subject skips the comparison permanently. The code (StoredToken.refreshed) skips the comparison only on the first refresh of a subject-less session; the refreshed subject is then adopted and later refreshes are checked. The ADR both overstated coverage and misstated the skip as permanent.

## Possible corrective rule

When a documentation-accuracy pass rewrites or touches a security guarantee in an ADR, check the guarantee against the code path for its edge cases (missing / empty input, first-call adoption) before keeping universal wording.

Classification (global vs epic, actionable vs knowledge) is deferred to the orchestrator.
