envelope_version=1
sender_type=plan
sender_id=plan-12-open-items-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-19T06:29:24Z

# Candidate lesson: two Sonar test-code smells reached the PR despite a green local verify -Ppre-commit

- Signal: automatic review (Sonar), sonar-issue findings 06bb85 and 543dd0 on PR #925; both fixed inline in the unified triage

## What happened

- 06bb85, `java:S1130` (MINOR): `BackChannelHttpRequestRefusalTest.shouldPropagateRequestRefusalUnchanged` (line 163)
  declared `throws Exception` that its body cannot throw. Fix: clause removed.
- 543dd0, `java:S5778` (MAJOR): `JwtPropertyKeysDerivationGuardTest` (line 141) built the excluded-key set inside the
  `assertThrows` lambda, so more than one call could throw. Fix: set built before `assertThrows`.

Both are in test files this plan created, and both passed the whole-tree `verify -Ppre-commit` used as pre-push evidence
(decision de9286). They surfaced only in the first Sonar round on the PR, which then needed a commit in 6-finalize (see the
separate git-workflow `no_sanction` candidate).

## Evidence

- Findings 06bb85 and 543dd0 resolution_detail.
- Decision log 2a51a2, b3a57d.

## Observation for the epic

The local pre-commit profile does not run these Sonar rules, so new test classes reach the PR with S1130 / S5778 smells that
then cost a finalize-phase source commit.
