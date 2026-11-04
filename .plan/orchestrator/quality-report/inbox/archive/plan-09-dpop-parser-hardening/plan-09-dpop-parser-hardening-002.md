envelope_version=1
sender_type=plan
sender_id=plan-09-dpop-parser-hardening
epic=quality-report
kind=candidate-lesson
created=2026-11-04T12:32:36Z

# Candidate lesson: a COVERED register row needs a wired test on every path it names

**Source plan:** plan-09-dpop-parser-hardening (PR #851)
**Signal:** review-bot finding abb54d (coderabbit inline, `doc/validation/cve-lessons-register.adoc:112`, resolution fixed by TASK-16)
**Suggested component:** project validation docs (`doc/validation/cve-lessons-register.adoc`, `doc/validation/threat-model.adoc` Wired-test rule)

## What happened

The CVE-2022-21449 (all-zero ECDSA signature) row was marked COVERED for both the access-token path and the DPoP-proof path. The DPoP half had only a direct `DpopProofValidator` unit test. That does not meet the threat model's Wired-test rule, which asks for a negative-path test that goes through `TokenValidator`. The review bot flagged the overstated status. TASK-16 added VTEST-16 in `WiredNegativePathAttackTest`, which sends an all-zero ES256 DPoP proof through `TokenValidator`, and the row and threat model were updated to reference it.

## Reusable rule

Before setting a CVE-register or threat-model row to COVERED, list every input path the row names (token, DPoP proof, JWE inner token, and so on). For each path, name the wired test that sends the attack input through the public `TokenValidator` entry point. A unit test on an inner validator is supporting evidence, not wired coverage. If one path has no wired test, add the test or mark the row PARTIAL. Do not let the status describe the paths as a group.

## Why it matters

The register is the project's evidence of security coverage. A COVERED status backed only by a unit test hides gaps in how the pipeline is wired, which is the exact failure class the Wired-test rule exists to catch.
