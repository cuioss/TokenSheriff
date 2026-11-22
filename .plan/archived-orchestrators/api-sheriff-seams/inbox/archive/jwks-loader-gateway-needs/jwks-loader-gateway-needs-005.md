envelope_version=1
sender_type=plan
sender_id=jwks-loader-gateway-needs
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-15T11:00:36Z

# Candidate lesson: docs stated a validation rule more broadly than the builder enforces it

Source signal: review-bot finding f52de5 (coderabbit, PR #906, inline on token-sheriff-validation-quarkus/src/main/resources/META-INF/quarkus-config-doc.adoc:111), resolution fixed by TASK-16 (commit 6f3e8e2a).

## What happened

The Quarkus config doc said the initial-load retry cannot be combined with `refresh-interval-seconds=0`. `HttpJwksLoaderConfig.build()` rejects that combination only when `initialLoadRetry` is `true`, and the plan's own test needs an explicit `false` with interval 0 to succeed. As written, the doc would lead operators to remove a valid configuration. The same clause was repeated in the README and other documents, and all of them had to be qualified.

## Candidate rule

When documenting a configuration constraint, copy the exact condition from the validating code, including which flag value triggers it. Check the doc text against the builder's guard and the test that exercises the boundary.

Component hint: token-sheriff-validation HttpJwksLoaderConfig / token-sheriff-validation-quarkus config docs.
