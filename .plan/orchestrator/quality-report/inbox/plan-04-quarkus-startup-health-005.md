envelope_version=1
sender_type=plan
sender_id=plan-04-quarkus-startup-health
epic=quality-report
kind=candidate-lesson
created=2026-10-29T15:41:27Z

component=token-sheriff-validation-quarkus
category=anti-pattern
source_plan=plan-04-quarkus-startup-health
source_records=b1f9f8, 651a2d (pr-comment, fixed)

# Documented startup and liveness guarantees were broader than what the code enforces

## What happened

Plan-04 added user-facing statements about startup validation and liveness. Both overstated what the
code enforces, and CodeRabbit flagged both:

- `doc/integration/health-checks.adoc:145` (b1f9f8) claimed that an invalid
  `sheriff.token.issuers.*` configuration fails the boot. In fact `IssuerConfigResolver` validates
  only enabled issuers, and settings of disabled issuers are never checked.
- `token-sheriff-validation-quarkus/README.adoc:35` (651a2d) claimed that a configuration or JWKS
  problem never triggers a container restart. In fact an invalid issuer configuration aborts startup
  (`TokenValidatorStartupInitializer` rethrows), so the container exits and its restart policy
  applies.

## Rule

- When docs state a guarantee ("X fails the boot", "Y never restarts"), check its exact scope against
  the enforcing code path before committing. Pay attention to conditions such as enabled/disabled,
  property-configured/external, and startup/runtime.
- Prefer naming the enforcing component and its condition over absolute wording ("always", "never",
  "any invalid configuration").

## Impact

Each overstatement cost a review round and a fix commit. In operations, an overstated liveness or
boot guarantee misleads people about restart behaviour.
