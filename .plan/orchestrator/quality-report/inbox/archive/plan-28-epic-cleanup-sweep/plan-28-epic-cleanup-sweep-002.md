envelope_version=1
sender_type=plan
sender_id=plan-28-epic-cleanup-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-11T11:54:22Z

# Candidate lesson: doc sweep for a stale build command missed sibling READMEs

Source: Q-Gate finding 5ae537 (phase 4-plan, scope_criterion_validator under_coverage, deliverable 2, resolution taken_into_account)

The criterion "no ./mvnw test instruction remains that the run proved fails" was operationalized over AGENTS.md and CLAUDE.md only. A content search for `mvnw test` found the same failing form in `token-sheriff-quarkus-parent/README.adoc` (line 30) and `benchmarking/benchmark-integration-wrk/README.adoc` (line 327).

Resolution in run: TASK-3 regenerated with both READMEs as write-replace steps.

Possible rule: when a deliverable retires an instruction form, derive affected files from a repo-wide content search for that form, not from the files the request names.
