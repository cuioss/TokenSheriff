envelope_version=1
sender_type=plan
sender_id=plan-09-public-helper-residuals
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-17T16:32:41Z

# Candidate lesson: Sonar new-code quality gate failed on PR #916 after a green local pre-push gate

**Signal source:** Q-Gate / CI findings resolved in-run (6-finalize ci-verify loop-back).

**Records:**
- 12:38:01Z `pre-push-quality-gate` completed `done` (whole-tree `verify -Ppre-commit`). Push and PR #916 creation followed.
- 12:47:12Z ci-verify dispatched verification-feedback triage. 12:50:01Z added TASK-014 "Clear the six SonarCloud new-code issues that fail the PR 916 quality gate". 12:50:35Z ci-verify `loop_back`, 6-finalize -> 5-execute. The caller reports the quality-gate failure at head d4cf9828.
- 13:09:45Z TASK-014 completed (commit 476a420f). It touched `BearerScopeValidationBuildStepTest`, `JwksEndpointHealthCheckIssuerStatusFailureTest`, `BoundedBodyHandler` and `BearerChallengeTest`, as mechanical cleanup with no behaviour change (per the later Q-Gate resolution notes).

**Observation:** The local `-Ppre-commit` profile and the in-house functional and code reviews let six Sonar new-code issues through. They showed up only after the push, at the SonarCloud PR gate. That cost a full loop-back: execute, both reviews again, pre-push gate, CI again.

**Possible corrective action (for the orchestrator to judge):** For plans whose footprint includes new test classes and hostile-input handling code, run the `sonar-roundtrip` read of open new-code issues (or a local Sonar-equivalent rule set) before the first push, not only after ci-verify. Or record the six rule ids from TASK-014 as project-level coding hints so later plans avoid them up front. The exact rule ids are in TASK-014 and the ci-verify triage record. They were not re-read here.
