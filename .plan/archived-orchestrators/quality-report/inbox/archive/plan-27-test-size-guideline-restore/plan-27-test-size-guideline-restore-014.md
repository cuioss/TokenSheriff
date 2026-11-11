envelope_version=1
sender_type=plan
sender_id=plan-27-test-size-guideline-restore
epic=quality-report
kind=candidate-lesson
created=2026-11-10T09:10:33Z

# Candidate lesson: CodeRabbit - security-reference.adoc overstated null-claim refusal for refresh tokens

- Source signal: review-bot finding `6732c1` (pr-comment, resolution `fixed`), PR #887 inline comment by coderabbitai at `doc/validation/security-reference.adoc:34`
- Plan: plan-27-test-size-guideline-restore
- Component: token-sheriff-validation documentation

## Record

CodeRabbit: "Limit the rejection claim to access and ID tokens." Refresh tokens do not undergo the `sub`/`iss` claim checks, and access and ID tokens reject a missing `iss` during issuer extraction, before issuer configuration lookup - not "at issuer lookup" as written.

## Fix

The claims-validation paragraph is now limited to access and ID tokens and names issuer extraction as the refusal point. Fixed in follow-up commit `abef336e`.

## Observation for the orchestrator

The documentation task (TASK-003) described null-claim handling uniformly across token types without checking the refresh-token pipeline, which skips those checks. Possible lesson: doc claims about validation behaviour should be verified per token pipeline (access, ID, refresh).
