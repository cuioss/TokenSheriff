envelope_version=1
sender_type=plan
sender_id=plan-28-epic-cleanup-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-11T11:56:25Z

# Candidate lesson: review bots caught documented build commands that did not do what they said

Source: the automatic-review signal. 14 pr-comment findings resolved `fixed` across PR #891 and the recreated PR #892 (CodeRabbit), remediated by fix tasks TASK-020 to TASK-025.

Recurring themes in the remediated review-bot findings:

1. **Documented Maven commands overstated what they run** (891b4b, 336613, 4cb4c7, 0da15b, ee7003; TASK-021, TASK-023):
   - A focused-test command still ran the client Failsafe suite and the verify-phase coverage check, so it could fail after the selected test passed. `-Dsurefire.failIfNoSpecifiedTests=false` also lets a misspelled selector pass with zero tests run.
   - The "all tests" `clean verify` triggers the integration-tests POM's pre-clean `stop-integration-container.sh --clean`, which tears down a running Compose stack. `-Dexec.skip=true` is not enough, because `clean` still deletes the bind-mounted `target/` and `target/retry-probe` log dirs.
   - A "unit tests only" `-am` command ran `WiredFlowNegativePathIT` through token-sheriff-client Failsafe. The integration-tests module has no Surefire tests of its own.
2. **Sanitisation applied to the WARN line but not the thrown message** (review body 011567; TASK-020): the thumbprint, jti, htm and htu rejections in DpopProofValidator formatted raw remote values into TokenValidationException.
3. **A test predicate narrower than the production predicate** (5da5d0; TASK-022, TASK-024): `isEscaped` in InsufficientScopeChallengeAttackDatabaseTest did not cover the bidi controls ErrorTextSanitizer now escapes, so a regression would pass.
4. **Doc wording drifted from code** (TASK-020: "presumed rotated" vs "presumed burned"; TASK-025: six doc and test-comment claims narrowed to what the build, ADR and tests guarantee).

Possible rules: (a) every build command a doc publishes must be run as written, with its claimed scope checked against the Surefire/Failsafe reports and plugin bindings (pre-clean hooks, -am upstream ITs). (b) A sanitisation fix covers both the log and the exception text. (c) Test oracles must mirror the full production predicate. Theme 1 drove three of the six review-fix loops.
