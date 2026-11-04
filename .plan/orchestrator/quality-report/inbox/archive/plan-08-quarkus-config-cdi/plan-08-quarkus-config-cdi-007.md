envelope_version=1
sender_type=plan
sender_id=plan-08-quarkus-config-cdi
epic=quality-report
kind=candidate-lesson
created=2026-11-02T11:41:10Z

# Candidate lesson: class-level @BearerAuth fixture tested only the positive path; native-reflection question left open

**Signal source:** automatic-review, CodeRabbit review body on PR #850 (finding cdb311, resolution fixed by TASK-19, commit 787fca74)
**Files:** token-sheriff-quarkus-integration-tests BearerAuthSpecIT.java, ClassLevelAuthEndpoint.java; token-sheriff-validation-quarkus BearerTokenInterceptor.java:193
**Suggested component:** pm-dev-java:junit-integration or plan-marshall:persona-module-tester (negative-control coverage), plus project-local follow-up

## What happened

`classLevelClassOnly` sent a valid token to `/jwt/class-level/class-only` and asserted 200. No test sent a request without a token to that endpoint, whose method has no annotation and relies only on the class-level `@BearerAuth(requiredRoles = {"user"})`. If the class-level binding were lost, which was the defect this plan fixed (Q-1), the suite would stay green. TASK-19 added a missing-token test that expects 401.

## Still open (not fixed in this plan)

- CodeRabbit suggested a native fixture with class-level `@BearerAuth` and without `@RegisterForReflection`, because both fixtures carry that registration and may hide a native reflection dependency in the class-annotation lookup. The plan sent this question to the maintainer, because settling it needs a native build.
- Two unverified CodeRabbit comments (native reflection metadata for the class-level lookup; fail-open when ArC reports an unannotated generated override) were not acted on.

## Candidate rule

A fixture that proves a security annotation is honoured must include a negative control (missing token -> 401, insufficient role -> 403) on the same class. A positive-only test passes whether or not the annotation is bound. For native-image paths, at least one fixture should omit `@RegisterForReflection` so that it exercises what a real application gets by default.
