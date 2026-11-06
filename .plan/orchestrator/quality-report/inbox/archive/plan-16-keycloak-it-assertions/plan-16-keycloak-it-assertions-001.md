envelope_version=1
sender_type=plan
sender_id=plan-16-keycloak-it-assertions
epic=quality-report
kind=candidate-lesson
created=2026-11-06T06:02:25Z

## Candidate lesson: the spec named the wrong OAuth error code, which grew a test-only plan into a production change

**Source signal**: Signal 2 (automated review), plan plan-16-keycloak-it-assertions, PR #868.

**What happened**: The plan spec said a wrong client secret makes Keycloak answer `invalid_client`. The real Keycloak IT run returned HTTP 401 with `unauthorized_client`. The plan was scoped as test-only (assert real Keycloak IT outcomes for TC-1, 3, 4 and 8, IT gap 10). To satisfy the observed behaviour it had to change the production library: `token-sheriff-client` `TokenEndpointClient` failure classification, plus `ClientAuthenticationFailedException` / `CredentialRejectedException` and their unit tests (`TokenEndpointClientTest`, `RefreshFailureClassificationTest`).

**Candidate rule**: When an epic spec states the exact wire response an external IdP gives (error code, HTTP status), check it against a real run, or the IdP's documented behaviour, before staging the plan. Otherwise word the spec as "the documented OAuth error class for X" and leave the literal value open. A plan whose asserted outcome is unverified should not be scoped as test-only: say up front that a production classification change may be needed.

**Scope hint for the orchestrator**: Epic-level spec authoring and re-grounding (plan-orchestrator spec review), with a possible project-local note for Keycloak behaviour in token-sheriff-client.
