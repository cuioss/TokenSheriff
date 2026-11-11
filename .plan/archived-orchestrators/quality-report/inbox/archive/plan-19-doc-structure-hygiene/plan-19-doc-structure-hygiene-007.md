envelope_version=1
sender_type=plan
sender_id=plan-19-doc-structure-hygiene
epic=quality-report
kind=candidate-lesson
created=2026-11-06T22:43:43Z

# Candidate lesson: renaming an AsciiDoc heading broke the old section anchor

- Source: CodeRabbit inline finding on PR #879 (thread PRRT_kwDOOA6qMM60tOSd), finding hash d81e1a, resolution fixed (inline fix, commit 57a7a40)
- File: doc/validation/specification/microprofile-jwt-compatibility.adoc:155
- Signal: automated review (review-bot finding remediated in run)

## What slipped

A structure-hygiene edit renamed a section heading. That changed the auto-generated AsciiDoc section ID and the GitHub permalink fragment, so existing links to the old fragment (_tokensheriff_advantages_beyond_mp_jwt) stopped resolving. The fix added an inline anchor carrying the previous generated ID next to the new heading.

## Possible corrective rule

When a documentation-hygiene task renames a heading in a published AsciiDoc document, keep the previous generated ID as an explicit inline anchor (or confirm no in-repo or external link uses it) in the same change.

Classification (global vs epic, actionable vs knowledge) is deferred to the orchestrator.
