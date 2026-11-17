envelope_version=1
sender_type=plan
sender_id=discovery-cache-verifier-hardening
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-16T22:25:44Z

# Candidate lesson: a coverage test whose expected set comes from the same constants it loops over cannot catch an omission

Source: pr-comment finding eabd86 (PR #912, coderabbitai, TokenSheriffClientDiscoveryCacheValidationTest.java:264), resolution fixed (TASK-14).

## Observation

The orphan-coverage test built its expected set from the same four constants its loop covered. A new
discovery-cache property added to `ClientRuntimeConfig` but missing from `Setting` would not have been
noticed, because both sides of the assertion would still contain only the old four.

## Fix

A new additive test derives the expected set from the `ClientRuntimeConfig` declarations under the
discovery-cache prefix, without the enabled flag and independent of `Setting.values()`, and requires
every one of them to be refused as an orphan. The existing assertions are unchanged.

## Candidate rule

A completeness test needs an oracle that does not depend on the registry it checks. Derive the
expected set from the declaring source (the config interface), never from the enum or list under test.
