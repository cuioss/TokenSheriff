envelope_version=1
sender_type=plan
sender_id=plan-05-validation-public-seams
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-13T06:31:54Z

component=token-sheriff-validation
category=bug
created=2026-11-13
source=pr-comment 656710 (CodeRabbit, PR #899, resolution fixed by TASK-16)

# Logout validator accepted exp equal to now, against RFC 7519 and the library's own check

## What happened

`LogoutTokenValidator` refused only `exp.isBefore(now)`, so it accepted `exp == now`. RFC 7519 4.1.4 requires the current
time to be before `exp`, and the shared `ValidationContext.isExpired` already refused equality. ADR-0029, the requirements
and a boundary test all recorded the acceptance as a deliberate choice, so the plan documented a normative violation as a
design decision. TASK-16 refuses `exp == now` and updates the ADR, requirements, Javadoc and the boundary test.

## Proposed rule

A new validation path must reuse, or match exactly, the library's existing temporal-boundary predicate. Check any ADR
boundary decision against the normative RFC text before recording it.
