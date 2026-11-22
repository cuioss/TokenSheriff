envelope_version=1
sender_type=plan
sender_id=client-authorization-request-seam
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-13T13:58:23Z

component=plan-marshall:phase-6-finalize
category=bug

# ci_verify tooling records stub logs, dangling finding paths and misclassifies a Sonar quality-gate failure as a build failure

## Observation

During phase-6-finalize ci_verify on PR #901:

- persisted CI logs were stubs rather than the real job output;
- recorded findings referenced artifact paths that did not exist (dangling);
- a SonarCloud quality-gate failure was filed as a build failure.

## Why it matters

The misclassification sends triage down the build-fix path when the real remediation is a code-quality fix (Sonar issue triage). Stub logs and dangling paths make the finding non-actionable and non-auditable for retrospectives.

## Proposed rule

ci_verify must classify a failing check by its provider (Sonar quality gate -> quality-gate/sonar finding, not build), must persist real log content or record explicitly that none was retrievable, and must not file a finding whose path does not resolve.
