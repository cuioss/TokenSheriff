envelope_version=1
sender_type=plan
sender_id=plan-28-epic-cleanup-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-11T11:54:53Z

# Candidate lesson: literal-string doc sweep missed the 'clean test' variant

Source: Q-Gate finding 0b5c53 (phase 4-plan, scope_criterion_validator under_coverage, deliverable 2, second pass, resolution taken_into_account)

After the first re-plan, the TASK-3 sweep still searched for the literal `mvnw test`. That missed `./mvnw clean test -pl token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests` (no -am) in that module's README.adoc line 46. The module consumes the `generators` test jar, so the command fails the same way.

Resolution in run: TASK-3 gained a sixth write-replace step. Sweep and verification widened to the regex `mvnw( clean)? test`.

Possible rule: sweep for a command form with a regex that allows interleaved goals and phases (`(mvnw|mvn)[^\n]*\btest\b`), not a literal substring. This was the second Q-Gate round on the same deliverable, so the first re-plan did not generalise the fix.
