envelope_version=1
sender_type=plan
sender_id=plan-15-plan-12-residual-fixes
epic=client-framework-aspects
kind=finding
created=2026-11-24T07:55:34Z

# PLAN-15: PR #952 review comments left for staging

PLAN-15 dispositioned all 23 CodeRabbit minor comments on #952 (4 fixed in f719225, 15 fixed in
PLAN-15, 4 declined). The items below need a separate plan.

## Declined: file outside PLAN-15's Expected Surface (each still applies at HEAD)

1. `doc/client/specification/token-handling.adoc` lines 114-115 — include verifier refusals
   (`RefreshResultVerifier`) in the documented domain of `RefreshFlow.classify`; line 114 still reads
   "exactly two sources".
2. `doc/client/specification/token-handling.adoc` lines 118-120 — keep the refusal-kind table in sync
   with `RefreshFailureClassification.Kind` (derive it, or require same-change updates as for the
   `ScopeDelta` table); the table still hardcodes four kinds with no control.
3. `benchmarking/doc/performance-scoring.adoc` line 136 — remove the claim that a single anomalous run
   does not flip the trend; the EWMA smooths the baseline, not the current score.
4. `benchmarking/README.adoc` line 43 — the `serve-reports.sh library` command is shown as the way to
   view results right after the `benchmark-jfr` command, but the script has no JFR target.

## Code-side follow-ups (PLAN-15 fixed the documentation side only)

1. ADR-0031: the deployment scope check evaluates an interface default method's scopes on their own;
   extend it to the merged set (class-level `@BearerAuth` plus the inherited default method) so an
   over-limit merged set fails deployment instead of answering 500 at request time.
2. `JfrBenchmarkRunner.createConfiguration()` overrides forks, iterations, times, threads and the include
   pattern with fixed values; decide whether it should keep `jmh.*` values when they are set.
3. `DiscoveryResolver.validateEndpointSchemes` lists the HTTPS-checked endpoints by hand; derive the set
   from the URL-valued `ProviderMetadata` fields, or add a guard test that fails when one is missing.
