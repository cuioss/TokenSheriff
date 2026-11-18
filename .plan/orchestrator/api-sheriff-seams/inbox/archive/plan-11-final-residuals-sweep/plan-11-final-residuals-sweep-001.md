envelope_version=1
sender_type=plan
sender_id=plan-11-final-residuals-sweep
epic=api-sheriff-seams
kind=finding
created=2026-11-17T22:34:03Z

# PLAN-11 edits one method of PLAN-10's ClientConfigurationTest

PLAN-11 (final-residuals-sweep, plan id `plan-11-final-residuals-sweep`, deliverable 5) changes
`ClientConfiguration` so every admitted `allowedEgressHosts` entry is stored in the canonical form
`EgressHostValidator.requireBareHost` returns (lower-case, a bare IPv6 literal in brackets), the form
`EgressPolicy`, `WellKnownConfig` and `HttpJwksLoaderConfig` already store. This is an operator decision
recorded at PLAN-11's outline.

It makes exactly one edit to `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/config/ClientConfigurationTest.java`,
confined to the method `shouldAcceptBareAllowedHostsAsConfigured` (lines 906-915 at `6bfc910d`):

- the method is renamed to state the canonical stored form;
- for the configured `List.of("Keycloak", "idp.internal", "10.0.0.5", "::1", "[2001:db8::1]")` its
  assertion now expects `List.of("keycloak", "idp.internal", "10.0.0.5", "[::1]", "[2001:db8::1]")`;
- it adds an assertion that a configuration built from `"KEYCLOAK"` equals one built from `"keycloak"`.

Nothing else in the file is touched; the other `getAllowedEgressHosts()` assertions already hold
canonical values and pass unchanged.

Action for PLAN-10: rebase over this edit when PLAN-11 lands; do not revert it. If PLAN-10 moves or
rewrites that method first, PLAN-11 makes the same assertion change at the new location only.
