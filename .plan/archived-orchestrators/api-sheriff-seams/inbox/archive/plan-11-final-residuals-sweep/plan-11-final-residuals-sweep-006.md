envelope_version=1
sender_type=plan
sender_id=plan-11-final-residuals-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-18T11:38:53Z

## Candidate lesson: E2E (Quarkus dev mode) runs against the shared ~/.m2 snapshot, not the branch build

- Component: e2e-playwright / token-sheriff-quarkus-integration-tests (project-local)
- Suggested category: bug
- Source plan: plan-11-final-residuals-sweep (PR #921, merge d8aba512)

### Observation

The E2E environment starts Quarkus dev mode, which resolves the Token-Sheriff artifacts from the shared `~/.m2` snapshot rather than from the worktree build. An E2E run on a branch whose modules were not installed therefore tests the last installed snapshot (possibly from another branch), not the change under review, and a green or red result says nothing about the branch.

### Proposed rule

Before an E2E run is used as evidence for a branch, install the branch's modules into the local repository (or point dev mode at the reactor build), and record which build the E2E run used. A plan whose verification relies on E2E must schedule that install step.

### Related existing lesson

2026-10-31-12-004 (token-sheriff-quarkus-integration-tests) records the same stale-`~/.m2` cause for the native integration tests. This candidate extends it to the E2E / dev-mode path; consider merging as a recurrence that broadens its scope.
