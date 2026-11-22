envelope_version=1
sender_type=plan
sender_id=plan-11-final-residuals-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-18T11:38:40Z

## Candidate lesson: review_support validate rejects a tracked `.plan/` file as evidence

- Component: plan-marshall:phase-6-finalize (review_support validate, evidence-at-graded-head check)
- Suggested category: bug
- Source plan: plan-11-final-residuals-sweep (PR #921, merge d8aba512)

### Observation

`review_support validate` refused a `.plan/` path cited as evidence with "does not exist at the graded head", although the file is git-tracked and present on disk. The existence check at the graded head appears to exclude or mis-resolve `.plan/` paths (for example by resolving them against the plan store rather than the git tree at that commit), so valid evidence was rejected.

### Proposed rule

The evidence-path check must resolve a cited path against the git tree of the graded head (`git cat-file -e {head}:{path}`), the same way for `.plan/` paths as for source paths. The refusal should say which root it probed, so a wrong-root failure is visible.
