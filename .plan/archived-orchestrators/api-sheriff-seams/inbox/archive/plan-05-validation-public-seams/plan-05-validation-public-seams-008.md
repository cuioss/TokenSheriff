envelope_version=1
sender_type=plan
sender_id=plan-05-validation-public-seams
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-13T06:31:34Z

component=token-sheriff-validation
category=anti-pattern
created=2026-11-13
source=qgate 6-finalize 66f77a (code-review, resolution taken_into_account)

# Requirement and Javadoc said 'present as a string' while code required a non-blank string

## What happened

`doc/validation/requirements.adoc:570` and the logout package Javadoc said `sub`/`sid` must be "present as a string".
The code and the security reference required a non-blank string. Code review flagged the drift.

## Proposed rule

When a validation rule is tightened (for example present, then non-blank), update requirements, Javadoc and the security
reference in the same change, using the same predicate wording as the code.
