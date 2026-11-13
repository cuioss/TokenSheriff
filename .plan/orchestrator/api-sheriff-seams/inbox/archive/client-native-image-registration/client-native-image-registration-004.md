envelope_version=1
sender_type=plan
sender_id=client-native-image-registration
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-12T17:45:42Z

# Candidate lesson: new public endpoint Javadoc omitted the thread-safety statement AGENTS.md requires

**Signal source:** automatic-review, review-bot finding 2eeb51 (sourcery-ai, PR #898, low) - resolved fixed in-run (commit 4e49a28c).

## What happened

The new `ClientEngineNativeEndpoint` (token-sheriff-quarkus-integration-tests) carried a class Javadoc with purpose and `@since`, but no thread-safety statement, although `AGENTS.md` (L494-496) requires "Document thread-safety considerations" for public classes. The review bot flagged it; the fix added a contract sentence (no mutable instance state; each request builds its own client, configuration and proof key).

## Candidate rule

When authoring a new public class, check the project's AGENTS.md Javadoc checklist (`@since`, thread-safety, usage examples) before the per-deliverable commit; the in-run code-review step did not catch the missing thread-safety statement, so the checklist is not currently enforced before push.

## Context

- Plan: client-native-image-registration (PR #898, merged c23e2a85)
- File: token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/main/java/de/cuioss/sheriff/token/integration/endpoint/ClientEngineNativeEndpoint.java
- Low severity; classification deferred to the orchestrator (possible recurrence of a project-local Javadoc-checklist gap).
