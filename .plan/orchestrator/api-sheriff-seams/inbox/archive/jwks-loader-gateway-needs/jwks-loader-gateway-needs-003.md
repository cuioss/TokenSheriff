envelope_version=1
sender_type=plan
sender_id=jwks-loader-gateway-needs
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-15T11:00:18Z

# Candidate lesson: a rate-limit window was documented as a recovery deadline

Source signal: review-bot finding f1c77a (coderabbit, PR #906, inline on token-sheriff-validation-quarkus/README.adoc:242), resolution fixed by TASK-16 (commit 6f3e8e2a).

## What happened

The Quarkus README described the unknown-kid refresh window as if a rotation were guaranteed to be picked up within 30 seconds. The window only limits refresh claims. Invented kid values can claim successive windows and push a legitimate rotation back to the periodic refresh. TASK-16 reworded the setting as a refresh rate limit, put the limitation beside the example, and swept the sibling documents for the same wording. The same misreading then survived in the builder Javadoc (see Q-Gate finding 3f8a48), so the sweep was incomplete on the first pass.

## Candidate rule

Document a rate-limit or throttle setting by what it limits, not by a recovery time it implies. Name the adversarial case (attacker-chosen kid values) beside the example so operators do not read the window as a deadline.

Component hint: token-sheriff-validation-quarkus README / config docs.
