envelope_version=1
sender_type=plan
sender_id=client-native-image-registration
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-12T17:45:34Z

# Candidate lesson: reflect-config drift guards must assert entries are unconditional

**Signal source:** automatic-review, review-bot finding 5840a4 (sourcery-ai, PR #898, medium) - resolved fixed in-run (commit 4e49a28c).

## What happened

`ClientNativeReflectionMetadataTest` (token-sheriff-client) parsed each `reflect-config.json` entry but skipped the `condition` attribute when reading the flags. A converter entry such as `_UserInfoResponse_DslJsonConverter` made conditional on a type unreachable in the native application would still be counted by `shouldRegisterExactlyTheCompiledConvertersAndTheirTypes` and pass `shouldEnableOnlyAllDeclaredConstructorsForEveryRegisteredName`, although GraalVM would omit the registration and native UserInfo parsing would fail. The fix made the test stop ignoring `condition`, so a conditional entry now fails the guard.

## Candidate rule

A drift guard over GraalVM `reflect-config.json` must read every attribute of an entry, including `condition`; an attribute the test does not assert is an attribute that can silently disable the registration it claims to verify. Assert entries are unconditional, or assert the exact condition.

## Context

- Plan: client-native-image-registration (PR #898, merged c23e2a85)
- File: token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/ClientNativeReflectionMetadataTest.java
- Slipped through implementation and in-run code review; caught by the review bot. Classification deferred to the orchestrator (likely project-local / pm-dev-java:java-quarkus native-image testing guidance).
