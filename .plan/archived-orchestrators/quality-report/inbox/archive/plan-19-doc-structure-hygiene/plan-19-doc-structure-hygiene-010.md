envelope_version=1
sender_type=plan
sender_id=plan-19-doc-structure-hygiene
epic=quality-report
kind=candidate-lesson
created=2026-11-06T22:44:05Z

# Candidate lesson: docs-only PRs skip the Sonar build, which leaves the Sonar count undecidable

- Source: automated-review / Sonar gate observation on PR #879, reported by the finalize dispatcher
- Signal: automated review (context of the review-bot gate)

## What happened

The repository's CI change check skips the Sonar build when a PR changes only documentation. PR #879 was documentation-only, so no Sonar analysis ran for its head. The finalize Sonar gate could not tell "zero Sonar issues" apart from "Sonar never analysed this head", so the count was undecidable and an operator had to decide whether to proceed.

## Possible corrective rule

When the change check skips the Sonar build for a documentation-only PR, the Sonar gate should read that skip as "not applicable" (a known, intended skip), not as an unknown count. Until then, an operator prompt is expected on every docs-only PR in this repository.

Classification (global vs epic, actionable vs knowledge) is deferred to the orchestrator.
