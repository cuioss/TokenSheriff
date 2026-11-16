envelope_version=1
sender_type=plan
sender_id=client-authorization-request-seam
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-13T13:58:27Z

component=pm-dev-java:ext-triage-java
category=improvement

# Triage should prefer a code fix over a suppression for a Sonar finding on plan-authored lines

## Observation

The unified triage proposed `@SuppressWarnings` for a new-code Sonar S2589 (condition always true/false) on lines this plan had written. The dispatcher overrode it with a real fix (null-safe `MoreStrings.isBlank`), because suppressing new-code issues violates the Java suppression standard.

## Proposed rule

For a finding located on lines the plan itself authored (new code), the triage agent must propose a code fix first and may propose suppression only with an explicit justification that the finding is a false positive and no reasonable rewrite exists. Suppression is the fallback for legacy/out-of-footprint code, not the default for new code.
