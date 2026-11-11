envelope_version=1
sender_type=plan
sender_id=plan-05-jwe-ecdh-es
epic=quality-report
kind=landing
created=2026-10-29T23:31:30Z

## What landed

plan-05-jwe-ecdh-es shipped as #835 (merged via the merge queue as e010c5cf).

```landing-facts
schema=landing-facts/1
plan_id=plan-05-jwe-ecdh-es
epic=quality-report
pr=#835
merge_state=merged
cleanup_owed=false
deliverables_total=2
deliverables_done=2
total_tokens=36873652
total_wall_seconds=46243.0
harness=claude
model=claude-sonnet-5-5
effort=unknown
plugin_version=0.1.2027
executor_version=0.1.2027
steps=finalize-step-sync-baseline:done,finalize-step-security-audit:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,adr-propose:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done
step.code-review.blocking_count=0
step.code-review.advisory_count=8
step.code-review.rules_cited=6
```

## Residue

- Scope grew well past the spec (V-1, VAL 8, V-17g): 9 fix tasks added hardening found by review, each round restarting the full gate, audit, review, CI and a rate-limited CodeRabbit cycle. The operator approved continuing at each gate. Added beyond the spec: ECDH-ES non-empty Encrypted Key and `kty=EC` rejection, single-reporter logging honouring quiet and opaque decode modes, CWE-117 sanitization of untrusted header text, fixed messages for header-decode and JWS parts-decode failures (the plain JWS decode message text changed), sanitized logging in the shared `JwkKey` decode (JWKS and DPoP paths), retirement of log record TokenSheriff-156.
- The pre-push gate's test-compile and module-tests arms were recorded DEGRADED (the gate document assumes a Python build); the orchestrator-tier whole-tree `verify -Ppre-commit` (3278 tests) was the real gate on the pushed tree. Coverage adequacy was not audited (`required_coverage_unknown`).
- CI at the final head failed once on an unrelated timing flake in `benchmarking/benchmark-core` (`LibraryMetricsExporterTest`, a 20 ms await) and passed on rerun.
- Metrics: two accumulator samples were first recorded from placeholder or interim figures and topped up; the pass-7 security-audit sample remains over-counted by about 28k tokens. The token total mixes populations (`totals_tokens_population: mixed`).
- Not fixed, documented as follow-ups: `token-decryption.adoc` line 200 over-generalizes the `kty` rejection (a non-string `kty` fails earlier with `FAILED_TO_DECODE_JWT`); the CWE-117 sanitizer is duplicated in `JweDecryptor` and `JwkKey`; test-coverage gaps (null/blank `epk` coordinate branch, `apu`/`apv` decryption, inner-JWS payload decode failure, the unsupported-compression matrix row); two test lines still hold raw U+2028/U+2029 characters.
- Tooling gaps seen: the installed `pre_merge_gate` has no `record-merge` verb although `branch-cleanup.md` prescribes it; four plan-marshall-side lessons could not be filed because this repo's lesson store rejects plan-marshall components.
- `archive-plan` had not yet run when this landing was written.
