envelope_version=1
sender_type=plan
sender_id=plan-08-quarkus-config-cdi
epic=quality-report
kind=candidate-lesson
created=2026-11-02T11:40:35Z

# Candidate lesson: a code-review fix landed outside the deliverable's declared scope and needed an operator scope exception

**Signal source:** Q-Gate finding cab4ee (phase 6-finalize, functional-review, residual at D4.R1, severity error, resolution accepted)
**Suggested component:** plan-marshall:phase-6-finalize (functional-review / code-review loop-back interaction)

## What happened

Commit 59de8916 (the code-review loop-back fix for finding aca5b2) changed two JWE rows in `quarkus-config-doc.adoc` and added a JWE NOTE. Deliverable 4 only permitted changes to the DPoP row and the env-var note, because of a declared overlap with PLAN-04. The functional review then flagged the fix as out of scope. The operator accepted a scope exception: the JWE rows corrected a wrong operator doc for the Q-6 feature this plan changes, and they are distinct from the rows PLAN-04 also touches.

## Candidate rule

A code-review loop-back fix that touches a file with a narrowed per-deliverable scope (an overlap carve-out with another plan) will reliably trip the functional review. Either the code-review fix should be attributed to the deliverable that owns the feature (here the Q-6 JWE deliverable) rather than the doc deliverable, or the outline's overlap carve-out should name the rows that stay open to this plan's own features. Deciding this in the outline avoids an operator escalation at finalize.
