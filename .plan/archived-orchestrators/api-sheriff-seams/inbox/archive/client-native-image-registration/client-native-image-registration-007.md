envelope_version=1
sender_type=plan
sender_id=client-native-image-registration
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-12T17:46:09Z

# Candidate lesson: review-bot fix in 6-finalize committed without a sanction and was refused

**Signal source:** script-failure cluster, notation `plan-marshall:workflow-integration-git:git-workflow` (work-log hash 4b2000, 2026-11-12T15:05:17Z, phase 6-finalize, during the verification-feedback / unified-triage wait region after automatic-review).

## What happened

After automatic-review on PR #898 surfaced two actionable sourcery-ai findings (5840a4, 2eeb51), the fix edits to `ClientNativeReflectionMetadataTest.java` and `ClientEngineNativeEndpoint.java` were committed via git-workflow in 6-finalize without a sanction. git-workflow refused with exit 1 (`script_internal_failure`, `reason: no_sanction`, `step: null`): "Source-tree commit refused in 6-finalize: no step id from the composed phase-6 manifest, no open loop-back fix task and no review-rubric section-6 inline-correction record." The run recovered (the fix landed as 4e49a28c and ci-verify / automatic-review / sonar-roundtrip re-fired green).

## Candidate rule

A source-tree commit in 6-finalize must carry its sanction: pass `--step` with the composed phase-6 manifest step id that owns the fix (for review-bot fixes, the triage/verification-feedback step), open a loop-back fix task, or record the section-6 inline correction - before calling the commit verb, not after the refusal.

## Context

- Plan: client-native-image-registration (PR #898, merged c23e2a85)
- Failing notation: plan-marshall:workflow-integration-git:git-workflow (commit, refused no_sanction)
- Classification deferred to the orchestrator (plan-marshall finalize fix-path guidance; check whether the verification-feedback workflow names the `--step` value its commits need).
