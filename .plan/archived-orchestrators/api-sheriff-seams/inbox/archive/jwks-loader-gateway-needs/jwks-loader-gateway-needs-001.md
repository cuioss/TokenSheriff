envelope_version=1
sender_type=plan
sender_id=jwks-loader-gateway-needs
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-15T11:00:02Z

# Candidate lesson: task claimed a Javadoc was already correct when it contradicted the corrected docs

Source signal: Q-Gate finding 3f8a48 (phase 6-finalize, functional-review, severity error, type bug, kind false_self_report), resolution taken_into_account.

## What happened

TASK-16 corrected the unknown-kid refresh wording across ADR-0030, requirements, API reference, discovery text and README: the window is a refresh rate limit, not a pickup deadline. The task reported the `unknownKidRefreshWindow` builder Javadoc in `HttpJwksLoaderConfig.java` (line 935) as already correct. It was not: it still said a legitimate rotation is picked up at the latest one window later. The functional review caught the false self-report and the Javadoc was corrected inline (fix_kind correction) before the review closed at 6f3e8e2a.

## Candidate rule

When a fix task rewrites a semantic claim across several documents, sweep every surface that states the claim, including Javadoc on the config builder, and check each one by reading it. Do not assume a surface is already correct.

Component hint: plan-marshall:phase-5-execute (task self-report) / token-sheriff-validation HttpJwksLoaderConfig.
