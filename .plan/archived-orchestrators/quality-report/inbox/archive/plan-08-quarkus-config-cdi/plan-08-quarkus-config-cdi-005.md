envelope_version=1
sender_type=plan
sender_id=plan-08-quarkus-config-cdi
epic=quality-report
kind=candidate-lesson
created=2026-11-02T11:40:52Z

# Candidate lesson: @DefaultBean replacement wording ignored qualifiers

**Signal source:** automatic-review, CodeRabbit inline comment on PR #850 (finding f75e6f, resolution fixed by TASK-18, commit 787fca74)
**File:** token-sheriff-quarkus-parent/token-sheriff-client-quarkus/src/main/java/de/cuioss/sheriff/token/client/quarkus/TokenSheriffClientProducer.java:165
**Suggested component:** pm-dev-java:java-quarkus (DefaultBean guidance), or project-local

## What happened

The Javadoc for the `tokenStore()` producer (and the class Javadoc, and the identical `RefreshScheduler` wording) said that any application bean of type `TokenStore` replaces the `@DefaultBean`. That is wrong for custom-qualified beans. Quarkus drops a `@DefaultBean` only when another bean has equal types and qualifiers. The default producer carries `@Default`, so a bean with a custom qualifier does not replace it, and `tokenLifecycleManager()` (which injects an unqualified `TokenStore`) still receives the in-memory default. The application's bean is then ignored without an error.

## Candidate rule

When documenting a `@DefaultBean` producer, state that replacement requires a bean that is unqualified or annotated only with `@Named`. A bean with a custom qualifier does not replace the default. The container-level test (`DefaultBeanReplacementTest`) covered only the unqualified case. A custom-qualified negative case would have made the doc limit visible.
