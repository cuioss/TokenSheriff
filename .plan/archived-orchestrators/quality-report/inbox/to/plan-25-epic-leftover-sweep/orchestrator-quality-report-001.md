envelope_version=1
sender_type=orchestrator
sender_id=orchestrator-quality-report
epic=quality-report
kind=finding
created=2026-11-07T09:21:44Z

## Operator decisions for PLAN-25 (2026-11-07)

The operator answered the four open questions recorded in the spec. These answers replace "ask the operator once at outline"; do not ask again.

1. **JWE size mismatch, item 7(k)(1): keep the 8 KiB parser limit and map the failure.** A token string above the parser limit must fail with a `TokenValidationException` (with the WARN and the security event the spec names), never an `IllegalArgumentException`. Nothing is newly accepted. The 32 KB statement in `JwtPropertyKeys` and the configuration documentation is corrected to the real limit. Required test: fails before, passes after, driven through `TokenValidator` (access, ID, refresh) and `BearerTokenProducer` with a 5-part token of about 10 KiB under a JWE-enabled issuer. The issue stays one deliverable of this plan unless the lifecycle cuts it at outline.
2. **Explicit JSON null identifier claims, item 7(i)(2): pin as observed.** Null is treated as absent. Add the `ClaimNameTest` row and reword `ClaimName.java` line 173 and `security-reference.adoc` line 46 as the spec says. `MapRepresentation` is not changed.
3. **Client back-channel egress policy, item 7(j)(1): the documentation follows the code.** No host-address egress policy is added to `BackChannelHttp`. Correct the documents and Javadoc named in 7(j) to what the code does.
4. **400-line test budget, item 5(c): a guideline, not a hard rule. More than 500 lines should be avoided.** State it in `AGENTS.md` as a guideline with the 500-line figure (not 400, not mandatory). No existing test class is split for this; test classes this plan creates or edits must stay at or under 500 lines, and a class that this plan's own edits would push past 500 is split in the same change (pure move, no assertion changed). Record the figures (52 classes over 400, 18 over 600, four at or over 1000) in the PR body as the existing state.

Everything else in the spec stands as written.
