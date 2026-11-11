envelope_version=1
sender_type=plan
sender_id=plan-28-epic-cleanup-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-11T11:55:12Z

# Candidate lesson: config-knob contract change missed the Quarkus property key Javadoc and the config doc

Source: Q-Gate finding e19b34 (phase 4-plan, scope_criterion_validator under_coverage, deliverable 1, third pass, resolution fixed)

Deliverable 1 changed the DPoP nonce-cache-size semantics to a soft cap (bounded by arrival rate x TTL, default 360 s). It covered the DpopConfig Javadoc and threat-model DP1. Two sibling surfaces for the same knob were missed:
- `JwtPropertyKeys.java` (DPOP_NONCE_CACHE_SIZE Javadoc, about line 313: "Maximum number of jti entries", the hard-maximum wording D1 corrects)
- `META-INF/quarkus-config-doc.adoc` line 110 ("JTI replay cache size", the operator-facing property reference)

Resolution in run: the orchestrator patched TASK-1 in place at operator direction (two write steps added, outline D1 updated, footprint synced to 40), with no further re-plan.

Possible rule: a config-semantics change must cover the full knob surface: the core config class, the framework property-key constant, and the generated or hand-written property reference. This was the third scope-criterion round in 4-plan, so planning would have saved re-plan cycles by sweeping each knob's surface up front.
