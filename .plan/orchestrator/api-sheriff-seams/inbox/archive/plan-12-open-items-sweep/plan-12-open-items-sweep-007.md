envelope_version=1
sender_type=plan
sender_id=plan-12-open-items-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-19T06:29:07Z

# Candidate lesson: builder refusal fixtures in EgressHostValidatorTest were hand-listed instead of derived from the Refusal enum

- Signal: automatic review (CodeRabbit), pr-comment finding 1d6c14 on PR #925, `EgressHostValidatorTest.java:48`; fixed inline in the unified triage

## What happened

PR 921 item (4) introduced the package-private enum `Refusal` and an `@EnumSource(Refusal.class)` test that guards
`REPRESENTATIVE_ENTRIES`. `builderRefusalClasses()` in the same test still listed the refusal classes by hand, so a new
`Refusal` constant with a representative entry but no builder row would pass, and `EgressPolicyTest`, `WellKnownConfigTest`
and `HttpJwksLoaderConfigEgressHostTest` would run without checking the new class. The fix draws one builder row per
constant from `Refusal.values()` plus the representative entries; extra boundary rows stay. CodeRabbit's suggested
switch-without-default was not adopted (the existing EnumSource test already fails on a missing representative entry).

## Evidence

- Finding 1d6c14 resolution_detail; reply posted 2026-11-18T21:27:15Z.
- Decision log 2a51a2 (inline fixes for 06bb85, 543dd0, 1d6c14 "close silent-pass or lint gaps"), b3a57d (commit of the
  three inline edits).

## Observation for the epic

When a closed set becomes an enum, every test that enumerates the set needs to derive from the enum in the same change,
not only the test that motivated the enum.
