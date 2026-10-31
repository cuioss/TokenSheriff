envelope_version=1
sender_type=plan
sender_id=plan-06-callback-logout-hardening
epic=quality-report
kind=candidate-lesson
created=2026-10-30T19:09:50Z

component=sonar-duplication
category=improvement
created=2026-10-30

# Sonar new-code duplication across modules was only visible in CI and forced late fix tasks

## Observation

PLAN-06 added CWE-117 sanitizing logic in two places: `LogSanitizer` (token-sheriff-client) and `InboundErrorNormalizer` (token-sheriff-validation). The local `verify -Ppre-commit` quality gate passed. The duplication only showed up when SonarCloud's new-code duplication gate failed in CI. Fixing it took extra execute work late in the plan. TASK-17/TASK-18 ("remove the duplicated sanitize logic between LogSanitizer and InboundErrorNormalizer (SonarCloud new-code duplication gate)") extracted the shared `ErrorTextSanitizer` into token-sheriff-validation (TASK-18 commit e22e62f1). That cost a loop-back, a re-run of the 3319-test sweep, and re-fires of security-audit, code-review and functional-review at the new head.

The extraction also left stale doc residue that the reviewers then flagged as advisories: the `InboundErrorNormalizer` Javadoc still said LS/PS are left untouched, and `LogSanitizerTest` comments still pointed at constants that had moved.

## Defect shape

No local gate measures cross-module duplication. Sonar's duplication metric runs only in CI, after push. When an outline adds "similar sanitization in module A and module B", nothing stops it before the remote gate does.

## Suggested direction

- At outline time, when two deliverables in different modules add logic of the same shape (sanitizer, normalizer, escaper), plan the shared helper in the lower module from the start (validation is already a dependency of client).
- Optionally run a local duplication check (for example CPD via the Maven PMD plugin, scoped to changed files) in the pre-push quality gate, so the Sonar duplication class is caught before push.
- When a helper is extracted, sweep the Javadoc and test comments of every former owner in the same task.
