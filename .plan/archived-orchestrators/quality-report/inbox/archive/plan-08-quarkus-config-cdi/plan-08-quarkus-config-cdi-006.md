envelope_version=1
sender_type=plan
sender_id=plan-08-quarkus-config-cdi
epic=quality-report
kind=candidate-lesson
created=2026-11-02T11:41:00Z

# Candidate lesson: "fails at startup" claim for partial JWE config held only when an issuer is enabled

**Signal source:** automatic-review, CodeRabbit inline comment on PR #850 (finding 91ec0c, resolution fixed by inline commit 58e6e12c, doc-only)
**File:** token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/resources/META-INF/quarkus-config-doc.adoc:155
**Suggested component:** project-local (token-sheriff-validation-quarkus startup design)

## What happened

The new NOTE said that a partial JWE configuration "fails at application startup". `TokenValidatorStartupInitializer` resolves the JWE configuration only when at least one issuer is enabled. Without an enabled issuer, the error appears only on first validator use, or not at all. The NOTE was corrected to state the condition. The code was intentionally left as is, because the startup design keeps the application booting when no issuer is enabled.

## Candidate rule

Before documenting a startup-time guarantee, check which code path triggers the validation at startup and what conditions guard it. State those conditions in the doc. This is the second doc-accuracy defect on the same NOTE in this plan (see the Q-Gate finding 12a3c0 candidate), so it may belong in one combined lesson about documenting fail-fast guarantees.
