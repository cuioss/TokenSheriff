# PLAN-16: Module residuals container (validation, benchmarking, client discovery)

epic: client-framework-aspects
workstream: WS-05

> Staged plan spec. SELF-SUFFICIENT: the emitted command is a one-line pointer to this file.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the hand-off contract.
> Staged 2026-11-24 under the epic's standing residual rule (see `epic.md` § Decisions): a residual goes into the
> staged plan that fits it by content, and a container plan for the modules concerned is opened only when no staged
> plan fits or the fitting plan would exceed 10 deliverables. None of these items fits a staged plan: PLAN-01 to
> PLAN-11 add new client, session and BFF capability, and PLAN-12 to PLAN-14 are the documentation baseline and
> change ids only. Later residuals of these three modules are appended here until it reaches 10 deliverables.

## Objective

Close the residuals PLAN-15's landing left in three modules, in one plan, after the documentation baseline is
final (PLAN-14): the cache and validation documentation defects, the benchmarking documentation and runner
defects, and one client discovery guard; and, appended 2026-11-25 from PLAN-13's landing, the follow-ups of the
requirement-id integrity guard and one vestigial sentence in the COMMONS requirements document. No requirement id
changes.

## Deliverables

1. **`AccessTokenCache` class Javadoc stops reading as a signature guarantee.** Today
   `AccessTokenCache.java:44-45` says the cache maintains "security through raw token verification on cache hits"
   and `:70` says it "verifies token content on each hit". Both describe only the hash-collision guard
   (`verifyToken`, line 210), and sit beside the sentence saying a hit does not verify the signature again. Reword
   both to name the collision guard and nothing more. Javadoc only: no code change, no test change.
   - Skills: pm-dev-java:javadoc
   - Persona: implementer
2. **Correct the validation documentation passages that contradict the code or each other.**
   - `doc/validation/architecture.adoc:36`: the last sentence ("the window ends when the token expires or is
     evicted") omits maximum token age and a DPoP or custom-rule rejection, which the same paragraph says are
     re-applied on a hit. Align it with the corrected `AccessTokenCache` Javadoc.
   - `doc/validation/architecture.adoc:262` and the `maxPayloadSize` row of the same file's table: "bounds token
     parts only" contradicts `token-sheriff-validation/doc/configuration/security-settings.adoc:56`, which says it
     also bounds string-supplied JWKS (`JwksParser.validateContentSize`). Make both agree with the code.
   - ADR-0017 (lines 21-27): split the run-on em-dash sentence into the two statements it carries (the readiness
     answer is cached for `sheriff.token.health.jwks.cache-seconds`, default 30 s; external-validator loaders are
     checked only when `sheriff.token.health.jwks.report-external-validator` is `true`, default `false`). Facts
     unchanged.
   - Skills: pm-documents:ref-documentation, pm-documents:ref-asciidoc
   - Persona: documenter
3. **Benchmarking documentation.**
   - `benchmarking/doc/local-testing.adoc:28-33`: the optional `serve-reports.sh stop` block gives the path from the
     repository root, but the Quick Start just above has already done `cd benchmarking/scripts`. Use one convention
     for the whole page.
   - `benchmarking/doc/performance-scoring.adoc:136`: remove the claim that a single anomalous run does not flip the
     trend. The EWMA smooths the baseline, not the current score; state what it does.
   - `benchmarking/README.adoc:43`: `serve-reports.sh library` is shown as the way to view results right after the
     `benchmark-jfr` command, but the script has no JFR target. Say how JFR results are viewed, or drop the
     implication.
   - Skills: pm-documents:ref-documentation, pm-documents:ref-asciidoc
   - Persona: documenter
4. **`JfrBenchmarkRunner.createConfiguration()` and the `jmh.*` properties.** The method overrides forks, iterations,
   times, threads and the include pattern with fixed values (`JfrBenchmarkRunner.java:57-73`), although the builder
   reads and validates the `jmh.*` properties first. Decide at outline whether a value the operator set explicitly
   wins over the fixed one. If it does, change the runner and its test; if it does not, keep the code and state the
   rule in `benchmarking/doc/JFR-Instrumentation.adoc` in one sentence. Either way, the documentation names the
   outcome.
   - Skills: pm-dev-java:java-core, pm-dev-java:junit-core, pm-documents:ref-asciidoc
   - Persona: implementer
5. **Derive, or guard, the HTTPS-checked endpoint set in `DiscoveryResolver`.** `validateEndpointSchemes`
   (`DiscoveryResolver.java:269`) lists the endpoints it checks by hand. A new URL-valued `ProviderMetadata` field
   would be silently unchecked. Derive the set from the URL-valued record components, or add a test that fails when
   a URL-valued component is not covered. No behaviour change for the fields checked today.
   - Skills: pm-dev-java:java-core, pm-dev-java:junit-core
   - Persona: implementer
6. **Requirement-id integrity guard follow-ups** (appended from `landings/PLAN-13.md`, L3-1 to L3-3). Work in
   `RequirementIdIntegrityTest` as PLAN-14 leaves it (CLIENT enforced, the `PENDING` entry gone); keep the absent-versus-
   explicit-zero predicate tests and the range-endpoint and repository-root tests PLAN-13 added.
   - **Check xref targets.** A reference such as `xref:../validation/requirements.adoc#COMMONS-11[...]` is also checked
     against the anchors of the document the xref names, not only against the prefix's own id set
     (`RequirementIdIntegrityTest.java:485-493` resolves the token only). Add a predicate test for a token that exists in
     its own catalog but not in the linked document.
   - **Sweep every file kind a citation can sit in.** `SCANNED_EXTENSIONS` (`:69`) holds `.adoc`, `.md` and `.java`, and
     `EXCLUDED_DIRECTORIES` (`:71-72`) holds `.github` and `.claude`. Decide at outline which further kinds (`.puml`,
     `.svg`, `.properties`, `.yml`, `.json`, `.xml`, scripts) carry citations today; add the ones that do, and state in the
     test's Javadoc the kinds left out and why.
   - **Cover the `return base` fall-through** of `resolveRepositoryRoot` (`:650-656`) with a test: no ancestor carries the
     markers.
   - **Size.** The class is 713 lines, over the `AGENTS.md` § Test Class Size guideline (500). Split the fixtures and
     parser helpers from the three checks, or give the reason in the pull request body. The parameterisation
     opportunity the code review named is part of the same decision.
   - Skills: pm-dev-java:junit-core, pm-dev-java:java-core
   - Persona: module-tester
7. **COMMONS "Former identifiers" intro.** `doc/commons/requirements.adoc:334` says "a sub-ID follows its parent", but
   COMMONS has no sub-ids; the sentence belongs to the VALIDATION table. Remove it from the COMMONS introduction
   (`landings/PLAN-13.md`, L2-3). One sentence; no id changes.
   - Skills: pm-documents:ref-asciidoc
   - Persona: documenter

**Done when:**

- `AccessTokenCache.java` contains neither "raw token verification on cache hits" nor "verifies token content on
  each hit" (both are present at `0545cffd`), and its Javadoc names the hash-collision guard as what the hit
  verifies.
- `doc/validation/architecture.adoc` no longer contains "bounds token parts only" (it does at `0545cffd`), and its
  line on the end of the cache window names maximum token age.
- ADR-0017's readiness passage is two sentences, and its numbers (30 s, `false`) are unchanged.
- `benchmarking/doc/performance-scoring.adoc` no longer contains "does not flip the trend" (it does at `0545cffd`).
- `benchmarking/README.adoc`'s single "View the results" step no longer implies that the `benchmark-jfr` run is
  served by `serve-reports.sh` (at `0545cffd` it follows all three commands, `:30-43`).
- A test fails when a URL-valued `ProviderMetadata` component is missing from the checked set: it fails against a
  deliberately incomplete set, and the fields checked today stay checked.
- A test fails when an xref names a requirement id that is defined in its own catalog but has no anchor in the
  linked document, and it fails against the case `xref:...#COMMONS-11[]` pointing at the validation document.
- The guard's Javadoc names the file kinds it sweeps and the kinds it leaves out; a test covers the
  `resolveRepositoryRoot` fall-through; the test class is at or under 500 lines, or the pull request body gives the
  reason.
- `doc/commons/requirements.adoc` no longer says a sub-ID follows its parent in the COMMONS "Former identifiers"
  introduction (it does at `1022071f`).
- The set of `CLIENT-`, `VALIDATION-` and `COMMONS-` ids is identical before and after.
- `./mvnw -pl token-sheriff-validation,token-sheriff-client,benchmarking/benchmark-core -am verify` is green.
- The AsciiDoc build of the documentation is clean, with no broken xref or link.
- The pull request's open Sonar read at the head lists zero new-code issues and zero hotspots left to review, with
  the rules left open named. A failed read is recorded as `unknown`, never as `0`.

## Standing Constraints

- Requirement ids are frozen (deliverable 6 changes how they are checked, never which exist); the three prefixes were renumbered by PLAN-13 and PLAN-14, so cite the ids as they
  stand when this plan runs.
- Documentation and Javadoc changes alter no behaviour. Deliverables 4 and 5 are the only code changes, and
  deliverable 5 changes no behaviour for the fields checked today.
- Never lose a normative statement; a sentence removed is redundant with a named surviving home or obsolete against
  the code (named with its evidence).
- ADR numbers are never reused; do not revive a deleted ADR.
- Build and test with Maven through the project's build skill.
- Sonar: an open finding is fixed in a pushed commit, or DISMISSED ONLY ON THE OPERATOR'S SELECTION, recorded as
  `operator-selected: {issue-key}: {reason}` and transmitted through
  `plan-marshall:workflow-integration-sonar:sonar_rest transition`. A dismissal the run cannot transmit is a
  blocker.

## Claim Labels

- OBSERVED: the class Javadoc says "maintaining security through raw token verification on cache hits" and "verifies token content on each hit" — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/cache/AccessTokenCache.java:44-45` and `:70` at `0545cffd` (orchestrator re-read, 2026-11-24, `landings/PLAN-15.md` L3-1).
- OBSERVED: `doc/validation/architecture.adoc:36` ends "The window ends when the token expires or is evicted" and `:262` says "`maxPayloadSize` bounds token parts only" — read at those lines at `0545cffd`; `security-settings.adoc:56` says it also bounds string-supplied JWKS (leaf reading, `landings/PLAN-15.md` L3-4; the code side, `JwksParser.validateContentSize` at `JwksParser.java:181-190`, was read by the leaf and not re-read by the orchestrator).
- OBSERVED: ADR-0017 lines 21-27 run two statements together in one em-dash sentence — read at `doc/adr/0017-A_JWKS_loader_reports_OK_only_with_usable_keys_and_retries_only_transient_discovery_failures.adoc:21-27` at `0545cffd`.
- OBSERVED: `local-testing.adoc:28-33` gives `benchmarking/scripts/serve-reports.sh stop` — read at `0545cffd`; the Quick Start above it does `cd benchmarking/scripts` (leaf reading, `landings/PLAN-15.md` L2-2).
- OBSERVED: `performance-scoring.adoc:136` says "so a single anomalous run does not flip the trend" — read at `benchmarking/doc/performance-scoring.adoc:136` at `0545cffd`.
- OBSERVED: `benchmarking/README.adoc:30-43` runs the `benchmark-core`, `benchmark-jfr` and `benchmark-integration-wrk` commands and then gives one "View the results at http://localhost:8080" step with `./serve-reports.sh library   # or: ./serve-reports.sh wrk` — read at `0545cffd` (orchestrator).
- HYPOTHESIS: `serve-reports.sh` has no JFR target (the PLAN-15 lane's report, finding `-001`) — confirm/refute at `benchmarking/scripts/serve-reports.sh` (verify-at-outline).
- OBSERVED: `JfrBenchmarkRunner.createConfiguration()` is declared at `benchmarking/benchmark-core/src/main/java/de/cuioss/sheriff/token/validation/benchmark/JfrBenchmarkRunner.java:57`; `DiscoveryResolver.validateEndpointSchemes` at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/discovery/DiscoveryResolver.java:269` — read at `0545cffd` (grep).
- HYPOTHESIS: the runner overrides the `jmh.*` values with fixed ones and the builder validates the properties first — confirm/refute at `JfrBenchmarkRunner.java:57-73` and `BenchmarkConfiguration.java:67-78` (verify-at-outline).
- HYPOTHESIS: `validateEndpointSchemes` lists the checked endpoints by hand and `ProviderMetadata` has URL-valued components it does not list — confirm/refute at `DiscoveryResolver.java:269` and `ProviderMetadata.java` (verify-at-outline).
- OBSERVED: the guard resolves a reference against the prefix's own defined ids only (`findDanglingReferences`,
  `RequirementIdIntegrityTest.java:485-493`); `SCANNED_EXTENSIONS = Set.of(".adoc", ".md", ".java")` at `:69`;
  `resolveRepositoryRoot` ends in `return base` at `:650-656`; the file is 713 lines — all read at `1022071f`
  (orchestrator re-read, 2026-11-25, `landings/PLAN-13.md` L3-1 to L3-3).
- OBSERVED: `doc/commons/requirements.adoc:334` carries "a sub-ID follows its parent" and COMMONS declares no `COMMONS-n.m`
  anchor — read at `1022071f` (orchestrator, `landings/PLAN-13.md` L2-3).
- HYPOTHESIS: file kinds other than `.adoc`, `.md` and `.java` carry requirement-id citations today — confirm/refute with a
  repository content search at outline (verify-at-outline); PLAN-13's leaf could not run one.
- Verify-first clause: if a fix would change a requirement id or behaviour beyond deliverables 4 and 5, or needs a file outside the Expected Surface, stop and report instead of widening the plan; the orchestrator folds it into the plan that fits, or appends it here.
- Not in this plan, deliberately: ADR-0031's deployment check evaluates an interface default method's scopes on their own. `ADR-0031:48` records that limit as ACCEPTED BY THE OPERATOR, so extending the check to the merged set is a reversal of an operator decision, not a residual. It is recorded as a Watch in `epic.md`.

## Expected Surface

- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/cache/AccessTokenCache.java`
- OBSERVED: `doc/validation/architecture.adoc`
- OBSERVED: `doc/adr/0017-A_JWKS_loader_reports_OK_only_with_usable_keys_and_retries_only_transient_discovery_failures.adoc`
- OBSERVED: `benchmarking/doc/local-testing.adoc`
- OBSERVED: `benchmarking/doc/performance-scoring.adoc`
- OBSERVED: `benchmarking/README.adoc`
- OBSERVED: `benchmarking/benchmark-core/src/main/java/de/cuioss/sheriff/token/validation/benchmark/JfrBenchmarkRunner.java`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/discovery/DiscoveryResolver.java`
- HYPOTHESIS: `benchmarking/doc/JFR-Instrumentation.adoc` (verify-at-outline)
- HYPOTHESIS: `token-sheriff-validation/doc/configuration/security-settings.adoc` (verify-at-outline; line 56 is the reference, edited only if it is the wrong side)
- HYPOTHESIS: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/discovery/` — new or extended test (verify-at-outline)
- HYPOTHESIS: `benchmarking/benchmark-core/src/test/java/` — a runner test, if deliverable 4 changes the code (verify-at-outline)
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/architecture/RequirementIdIntegrityTest.java` (deliverable 6)
- OBSERVED: `doc/commons/requirements.adoc` (deliverable 7)
- HYPOTHESIS: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/architecture/` — a split-out helper class, if the size
  decision of deliverable 6 splits the guard (verify-at-outline)

## Dependencies and Sequencing

- Depends on: PLAN-14 (the documentation baseline is final and the ids renumbered; runs first).
- Overlaps with: PLAN-13 and PLAN-14 on `doc/` and `benchmarking/doc/` (strictly sequential, hence the dependency); PLAN-14
  also edits `RequirementIdIntegrityTest` (it removes the CLIENT `PENDING` entry), so deliverable 6 starts from PLAN-14's
  version of that file. No staged plan from PLAN-01 to PLAN-11 shares a file with it.
- Adjacent to: `doc/validation/requirements.adoc` and `doc/client/` documents, which stay untouched.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/client-framework-aspects/plans/PLAN-16-module-residuals.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates and edits
NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message. The orchestrator
owns every other ledger write. The plan reports its outcome through its PR and its inbox message. See
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
