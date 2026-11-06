envelope_version=1
sender_type=plan
sender_id=plan-16-keycloak-it-assertions
epic=quality-report
kind=candidate-lesson
created=2026-11-06T06:02:41Z

## Candidate lesson: IT assertions only checked the status code, so they could pass when the server never ran the check

**Source signal**: Signal 2 (automated review). Finding `4d8748`: a CodeRabbit review-body comment on PR #868 (4 nitpicks, reviewed commit fc5084a). It was fixed in the run by TASK-8, fix commit 589e08a.

**What happened**: Four Keycloak IT assertions accepted responses that an unrelated failure could also produce:
- `LogoutSpecIT` (unregistered post-logout URI): asserted only that no redirect happened. An HTTP 500 without `Location` would also pass. It now asserts the rejection response itself.
- `LogoutSpecIT` (positive redirect): checked the Location by parts. A Location with userinfo or a fragment could pass. It now compares the complete Location, including the echoed state.
- `AuthCodePkceSpecIT` (mismatched verifier): accepted any 400. It now asserts `invalid_grant` and that no `access_token` is present.
- `MixUpSpecIT` (honest control): used `assertThrows(RuntimeException.class, ...)`. A local failure after discovery would pass. It now requires `CredentialRejectedException`.

**Candidate rule**: A negative-path IT that claims "the server rejected X" must assert the server's specific rejection: error code, the absence of the success artifact, and the specific exception type. A bare status code, a missing header, or a supertype exception is not enough. Each assertion should fail if the request failed for an unrelated reason.

**Scope hint for the orchestrator**: Test-authoring standards (junit-integration / persona-integration-tester), or a project-local IT convention for token-sheriff-quarkus-integration-tests.
