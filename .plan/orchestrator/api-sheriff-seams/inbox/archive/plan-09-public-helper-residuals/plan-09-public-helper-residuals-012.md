envelope_version=1
sender_type=plan
sender_id=plan-09-public-helper-residuals
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-17T16:33:20Z

# Candidate lesson: module_mapping_validator flags Javadoc-only and test-only deliverables; test-impl-mapping.adoc lacks rows

**Signal source:** Q-Gate findings, 4-plan (q-gate-validation). Four `module_mapping_validator` rows plus one `skills_by_profile` row, all resolved in-run as `accepted` by the operator.

**Records:**
- cda16f (deliverable 1): the deployment-module test pins `TokenSheriffProcessor`. The sibling implementation task also writes three validation-quarkus files that only change comments or Javadoc.
- 1fa2a6 (deliverable 3): `InsufficientScopeChallengeRoundTripTest` in token-sheriff-client is an intentional cross-module pin of `commons.http`. `doc/developer/test-impl-mapping.adoc` mentions it only in prose, with no test-path / impl-path / reason row.
- 4460b7 (deliverable 4): the module_testing "target" is a main-source Javadoc-only edit (`IssuerConfigResolver`), not a test file.
- 8d215f (deliverable 5): a test-only fix plus an ADR convention check, with no production counterpart.
- 298c7b: module `documentation` has an undeclared-empty `skills_by_profile.module_testing`.

**Observation:** In 4 of 5 deliverables the validator flagged pairings that were deliberate. Each needed an operator disposition before execute could start. Every message offered the same remedy: add a row to `doc/developer/test-impl-mapping.adoc`. None was added, so the next plan in this repository that routes a cross-module pin or a Javadoc-only target will be flagged the same way.

**Possible corrective action (for the orchestrator to judge):** Either add the structured rows to `doc/developer/test-impl-mapping.adoc` (client round-trip -> commons.http, deployment-module tests -> TokenSheriffProcessor), or have phase-4-plan profile Javadoc-only edits as `implementation` rather than `module_testing`. Declaring `documentation.module_testing` as deliberately minimal (`minimal: true`) would silence 298c7b.
