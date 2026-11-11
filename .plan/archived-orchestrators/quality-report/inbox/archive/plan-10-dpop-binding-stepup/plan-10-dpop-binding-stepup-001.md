envelope_version=1
sender_type=plan
sender_id=plan-10-dpop-binding-stepup
epic=quality-report
kind=candidate-lesson
created=2026-11-04T20:51:46Z

# Best-practice prose stated the DPoP cnf.jkt rejection without the JWT-only scope the code and spec use

**Source**: review-bot finding 8bccfd (CodeRabbit, PR #856, inline at doc/client/best-practices.adoc:85), resolution fixed in commit 310669e. Slipped-then-caught defect.

## What happened

The plan added a "Sender-constrain retrieved tokens" practice to `doc/client/best-practices.adoc` that said to reject an issuance response with "an absent or different `cnf.jkt`". The client (`TokenEndpointClient`) applies the `cnf.jkt` comparison only to JWT access tokens; an opaque access token with `token_type` DPoP passes. `doc/client/specification/token-handling.adoc` already stated the JWT-only scope correctly. So the summary document contradicted both the code and the normative spec.

Neither the functional review nor the code review (both ran several full rounds over the same 23 files) caught the missing qualifier; the external review bot did.

## Candidate rule

When a plan restates a normative rule in a summary/best-practice document, copy its scope qualifiers (here: "for a JWT access token") from the spec section that owns it, and have the review compare the restatement against that owning section rather than only against the code.

## Evidence

- Finding 8bccfd: "Scope the `cnf.jkt` rejection rule to JWT access tokens ... `token-handling.adoc` states the JWT-only scope correctly, so this practice should use the same scope."
- Fix: commit 310669e (inline disposition during the wait-region triage), reply sent and thread resolved.
