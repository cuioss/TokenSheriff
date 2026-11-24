envelope_version=1
sender_type=plan
sender_id=plan-12-documentation-review-consolidation
epic=client-framework-aspects
kind=candidate-lesson
created=2026-11-23T20:28:23Z

# Candidate lesson: condensing an ADR must keep its Status section a single status token

Source signal: Q-Gate finding d60f5e (6-finalize, code-review, severity error, fix_kind correction), resolution
taken_into_account (code review closed at ddad84a3 with no blocking finding after the loop-back).

## Observation

While condensing ADR-0010 (`doc/adr/0010-A_refresh_token_the_authorization_server_declares_invalid_is_a_third_classification_state.adoc:17`),
the Status section stopped being a single status token. `manage-adr` parses that section, so the ADR dropped out of
the Accepted listing. Code review caught it and forced a loop-back iteration in finalize.

## Candidate rule

When editing or condensing an ADR, keep the Status section machine-readable: exactly one status token
(e.g. `Accepted`). Put amendment or supersession prose elsewhere in the record. After an ADR-reduction pass, run
the `manage-adr` listing and confirm every surviving ADR still appears under its status.

Component hint: plan-marshall:manage-adr (format contract), or project docs convention for doc/adr.
