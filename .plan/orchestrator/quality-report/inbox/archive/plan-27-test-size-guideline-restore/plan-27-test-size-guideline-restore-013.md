envelope_version=1
sender_type=plan
sender_id=plan-27-test-size-guideline-restore
epic=quality-report
kind=candidate-lesson
created=2026-11-10T09:10:32Z

# Candidate lesson: CodeRabbit - CLAUDE.md claimed verify -Ppre-commit runs all tests

- Source signal: review-bot finding `ce1f7a` (pr-comment, resolution `fixed`), PR #887 inline comment by coderabbitai at `CLAUDE.md:31`
- Plan: plan-27-test-size-guideline-restore
- Component: project documentation (CLAUDE.md / AGENTS.md)

## Record

CodeRabbit: "Do not claim that the quality gate runs all tests." `verify -Ppre-commit` skips the Quarkus integration tests, which only run under the `integration-tests` profile.

## Fix

CLAUDE.md no longer claims the gate runs all tests and now names the separate integration-test command (`./mvnw clean verify -Pintegration-tests -pl token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests -am`), matching AGENTS.md. Fixed in follow-up commit `abef336e`.

## Observation for the orchestrator

A slipped-then-caught doc inaccuracy: the plan wrote a build-gate claim into CLAUDE.md that disagreed with AGENTS.md. Remediating it required a source-tree commit in 6-finalize, which triggered the git-workflow refusal recorded as a separate candidate.
