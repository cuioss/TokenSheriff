# Token-Sheriff - AI Agent Guidance

Token-Sheriff is a high-performance OAuth 2.0 and OpenID Connect token validation library for Java/Quarkus applications. This document guides AI coding agents when working with the codebase.

## Dev Environment Tips

### Build System
- **Build tool**: Maven, via the wrapper (`./mvnw`) — never a locally installed `mvn`
- **Java version**: as declared by `maven.compiler.release` in the root `pom.xml`
- **Primary framework**: Quarkus, pinned by `version.quarkus` in the root `pom.xml`

Deliberately no version numbers here: this file is not a source of truth for them and
silently goes stale. Read the root `pom.xml` and `.mvn/wrapper/maven-wrapper.properties`
when a concrete version matters.

### Build-Configuration Verification

This project inherits its build configuration from `cui-java-parent`, so the `pom.xml` files in this
repository are not the effective configuration. The authoritative resolution command is:

```bash
./mvnw help:effective-pom
```

That bare form is recursive: it walks the whole reactor and emits one effective POM per module,
wrapped in a single `<projects>` element. `.mvn/maven.config` pins only `-T1C`, which sets build
parallelism and does not change that recursion — pass `-N` to restrict the output to the invoked
(root) project. What the resolution does not show is configuration that lives in a profile which is
currently inactive (for example `pre-commit`): it reflects only the profiles active for that
invocation. Activate the profiles and narrow to the module that the claim is actually about:

```bash
./mvnw -Ppre-commit help:effective-pom -pl <module>
```

- **Before agreeing that a mechanism is missing or unconfigured**, read the parent POM chain and the
  effective POM. A repo-scoped search alone proves nothing about inherited configuration: absence
  from this tree is not absence from the build.
- **Before adding a `<repositories>` or `<pluginRepositories>` declaration** — or any element Maven
  merges by id, such as a plugin `<execution>` — walk the parent chain to its root and read the
  effective POM first. Maven keys these elements by `id`: an entry whose id matches an inherited one
  merges into that entry instead of being appended beside it, with the child's values winning field
  by field while fields the child omits stay inherited from the parent. Reusing an id therefore
  partially overrides the inherited entry rather than adding a second channel. Never reuse a
  super-POM reserved id such as `central` for an additive channel.
- **Before believing a build assertion enforces anything, check its phase against the phase of
  every mutating execution it is meant to police.** An assertion must not run after a mutation it
  polices: the mutation would have already normalised the tree, so the assertion inspects its own
  fixture and cannot fail by construction. A green exit code then proves nothing, and a clean tree
  observed afterwards is evidence that the mutation ran — not that the gate enforces anything.
  Resolving the phase is not optional guesswork: **an `<execution>` with no declared `<phase>`
  takes its goal's default phase from the plugin descriptor**, which no POM in this repository
  shows. Read the descriptor (`./mvnw help:describe -Dplugin=<groupId>:<artifactId> -Ddetail`) or
  the plugin's documentation; never assume "no phase" means "not bound". This is exactly how
  `rewrite:run` (descriptor default `process-test-classes`) silently preceded the `verify`-phase
  `rewrite:dryRun` assertion that was supposed to police it.
- **Before adopting an unreleased snapshot version-property pin**, enumerate every artifact that
  property governs — the main jar and every classifier — and verify each one's actual contents from
  the remote repository rather than a possibly-warm `~/.m2`. Compare class counts against the last
  known-good release.

### Project Structure
Multi-module Maven project:
- `token-sheriff-validation/` - The core Token-Sheriff validation library
- `token-sheriff-quarkus-parent/` - Quarkus framework integration
- `benchmarking/` - Performance benchmarking modules
- `bom/` - Bill of Materials for dependency management

### Essential Build Commands
```bash
# Development build
./mvnw clean verify

# Full build with all tests
./mvnw clean install

# Build single module
./mvnw clean install -pl <module-name>

# Run single test
./mvnw test -Dtest=ClassName#methodName
```

### Code Standards
- **Indentation**: 4 spaces (configured in `.editorconfig`)
- **Line endings**: Unix-style (LF)
- **Encoding**: UTF-8
- **Java features**: Use the modern Java features the configured release level allows (records, sealed classes, pattern matching, text blocks)
- **Lombok**: Use `@Builder`, `@Value`, `@NonNull`, `@ToString`, `@EqualsAndHashCode` appropriately
- **Preferred overloads**: Before adding an overload that production code will prefer over an
  existing public method, ask two questions. Is the existing method overridable — is it public or
  protected on a non-final class? And does anything actually override it? Include test doubles in
  that second answer: subclasses of a production class are the population most likely to override a
  method and least likely to be thought of as consumers, so a search that looks only at production
  callers will report "nothing overrides it" and be wrong. If anything does override it, adding the
  preferred overload is a behavioural break for every overrider — their override stops being
  called — regardless of the change being source- and binary-compatible. Compatibility is not the
  test; dispatch is. See ADR-0006 for the decision this check enforces.

  Reviewer corollary: a test that extends a production class and starts timing out is a first-class
  suspect for a dispatch-target change, not a flake. Re-read the dispatch path before re-running it,
  retrying it, or marking it flaky — a timeout is what an override that no longer runs looks like
  when the real implementation blocks on I/O the double was there to avoid.

### Logging Standards
This project uses CUI logging standards with Java Util Logging:
- Logger: `de.cuioss.tools.logging.CuiLogger` (private static final LOGGER)
- **Format specifier**: Always use `%s` for parameter substitution (NEVER `{}`, `%.2f`, `%d`)
- **Structured logging**: Use `de.cuioss.tools.logging.LogRecord` for INFO/WARN/ERROR messages
- **LogRecord ranges**: INFO (001-099), WARN (100-199), ERROR (200-299)
- **Documentation**: All log messages must be documented in `doc/LogMessages.adoc`
- **Exception logging**: Exception parameter always comes first

## Testing Instructions

### Testing Framework
- **Primary**: JUnit 5 (Jupiter)
- **Test patterns**: AAA pattern (Arrange-Act-Assert)
- **Coverage requirement**: two independent rules apply, at different granularities and on different lanes. The rule inherited from `cui-java-parent` is `element=BUNDLE` and gates **both** counters — `INSTRUCTION COVEREDRATIO >= 0.80` **and** `BRANCH COVEREDRATIO >= 0.80` — so it counts instructions and branches, never *lines*. It is declared inside the opt-in `coverage` profile, so a plain `./mvnw clean verify` never applies it. In `token-sheriff-client` the five refresh-path classes (`flow.RefreshFlow`, `token.RotationResult`, `token.RefreshTokenFamily`, `lifecycle.RefreshScheduler`, `lifecycle.InMemoryTokenStore`) carry a stricter per-class floor — `BRANCH >= 0.80` **and** `INSTRUCTION >= 0.90` — enforced on the **default** lane, so a plain `./mvnw clean verify` fails on a refresh-path coverage regression. See `doc/client/specification/test-strategy.adoc` § Coverage gates.
- **Coverage check**: the SonarCloud new-code quality gate in CI is the coverage authority; `./mvnw clean verify -Pcoverage` is an opt-in local diagnostic for the module-wide bundle rule, not a merge gate; the refresh-path per-class gate needs no profile flag and already runs under `./mvnw clean verify`.
- **Signal integrity**: the rules for proving a check discriminates before trusting its verdict — coverage-gate negative controls, measuring from `clean`, no-verdict versus failing-verdict, and asserting identity rather than a proxy property — are stated once in `doc/client/specification/test-strategy.adoc` § Signal integrity, and that section applies to every module in this repository.

### CUI Test Generator
This project has CUI Test Generator dependencies available for test data generation:
- Provides type-safe, consistent test data generation
- Use `@CsvSource` for simple data
- Use `@ValueSource` for single parameter variations
- Use `@MethodSource` for complex parameterization

### Parameterized Tests
Mandatory for 3+ similar test variants. Common annotations:
1. `@CsvSource`
2. `@ValueSource`
3. `@MethodSource`

### Test Execution Commands
```bash
# Run all tests
./mvnw test

# Run integration tests
./mvnw clean verify -Pintegration-tests -pl token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests -am

# Run micro-benchmarks
./mvnw clean verify -pl benchmarking/benchmark-core -Pbenchmark

# Run integration benchmarks
./mvnw clean verify -Pbenchmark -pl benchmarking/benchmark-integration-wrk
```

### Pre-Commit Validation

**CRITICAL**: Execute this sequence before ANY commit:

1. **Quality verification**:
   ```bash
   ./mvnw -Ppre-commit clean verify
   ```
   - Fix ALL errors and warnings (mandatory)
   - Address OpenRewrite markers (see section below)
   - This **auto-fixes**: it rewrites your files in place (`license:format`, `rewrite:run`).
     Review what it changed and commit it. A green run that produced no diff and a green run
     that repaired the tree look identical — always check `git status` afterwards.

2. **Final verification**:
   ```bash
   ./mvnw clean install
   ```
   - Must complete without errors or warnings
   - All tests must pass

3. **Integration tests**:
   ```bash
   ./mvnw clean verify -Pintegration-tests -pl token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests -am
   ```

Tasks are complete ONLY after all three steps succeed.

### OpenRewrite Markers - Critical Understanding

Pre-commit builds run OpenRewrite recipes that add markers to flag violations.

**Marker pattern**: `/*~~(TODO: INFO needs LogRecord)~~>*/` or `/*~~(TODO: [message])~~>*/`

**What markers indicate**:
Markers indicate ACTUAL BUGS:
- Placeholder/parameter count mismatches: `"value: %s"` with 0 parameters
- Wrong format specifiers: Using `%.2f`, `{:.2f}`, `{}`, `%d` instead of `%s`
- Missing LogRecord definitions for production INFO/WARN/ERROR logs
- Generic Exception usage instead of specific types
- RuntimeException catches that should be specific exceptions

#### Rewrite OUTPUT vs rewrite REQUEST-TO-ACT

A rewrite run produces two categorically different things, and conflating them is how markers end
up committed:

- A rewrite **output** is a transformation the recipe already applied — formatted whitespace,
  ordered imports, a removed unused import. It is finished work, and it is committed as-is.
- A rewrite **request-to-act** is an injected `/*~~(TODO: … Suppress: …)~~>*/` marker. The recipe
  could not fix the code itself, so it is asking a human to act. It is an open work item, never a
  result. Committing it verbatim ships the request instead of the response.

**Worked example.** Four markers were once committed to this repository verbatim — three
`InvalidExceptionUsageRecipe` markers in `token-sheriff-client` and one
`CuiLogRecordPatternRecipe` marker in `BearerTokenProducer`. Each was a request the author left
unanswered. Reading the whole rewritten tree as "the tool's result" is the mistake: the formatting
was a result, the markers were not.

Acting on a request-to-act does not always mean changing the flagged code — sometimes the correct
answer is that the recipe is wrong, and the act is to record that fact in the sanctioned
suppression form (see below).

**Never commit code with markers present.**

#### Handling strategy

Production code violations:
- Fix the actual bug (add missing placeholders, change format to %s)
- Create LogRecord constant for INFO/WARN/ERROR messages
- Replace generic Exception with specific types (IOException, IllegalStateException, etc.)
- Never catch or throw RuntimeException - use specific exception types

Test code violations:
- For diagnostic/performance logging: Add suppression comment at class level:
  ```java
  // cui-rewrite:disable CuiLogRecordPatternRecipe
  // This is a test/utility class that outputs diagnostic information for analysis
  ```
- For exception handling: Replace RuntimeException with AssertionError in test failures
- For format bugs: Fix placeholder mismatches even in tests (change to %s)

#### `InvalidExceptionUsageRecipe`: narrow the catch, do not suppress

Suppression is **not** a valid strategy for `InvalidExceptionUsageRecipe`. The standing remedy is
to narrow the caught exception type against the **throwers the callee actually declares** — except
where the caught failures come from an **open set of SPI implementations that declare no
`throws`**, in which case there is nothing to narrow against and suppression is legitimate.

That carve-out is the rule's single limit, and it is part of the rule rather than a footnote: the
tree already holds its one sanctioned instance at
`token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/pipeline/TokenBuilder.java:143`,
where custom SPI claim mappers are translated into `TokenValidationException`. Everywhere else,
narrow.

Two practical notes from the sites already narrowed:

- Narrow against the **declared** throwers, not against what a test double happens to throw. When a
  test double throws a type the API does not declare, the double is what is wrong.
- Where a `catch` was load-bearing for an invariant — completing a future, releasing a latch —
  make the `finally` block satisfy that invariant unconditionally **before** narrowing the catch.
  Narrowing first opens a window in which an unnamed throwable escapes with the invariant broken.

#### `CuiLogRecordPatternRecipe`: demonstrated false positives are suppressible in production code

The class-level suppression form above is sanctioned in **production** code too, not only in test
and utility classes — but only for a *demonstrated* recipe mis-fire on a call that is already
correct, and only when the suppression is accompanied by a comment recording the specific mis-fire.
The demonstration is required: this sanction is for proven false positives, never for silencing an
inconvenient finding.

**Worked instance.** `BearerTokenProducer` calls
`LOGGER.warn(e, BEARER_TOKEN_VALIDATION_FAILED, e.getMessage(), e.getEventType())`. The recipe
mis-fires on the `warn(Throwable, LogRecord, Object...)` overload, mistaking the leading
`Throwable` for the format argument; the `LogRecord` template carries exactly two matching `%s`
placeholders and the call is correct. The remedy was a class-level
`// cui-rewrite:disable CuiLogRecordPatternRecipe` plus that explanation.

**Corollary: never contort a correct logging call to satisfy a buggy recipe.**

#### `CuiLoggerStandardsRecipe` rewriting `%n` to `%s` is semantic damage

`CuiLoggerStandardsRecipe` misreads the argument-less `%n` line-separator conversion as a value
placeholder and rewrites it to `%s`, adding a format placeholder with no matching argument and
corrupting the format string. This is semantic damage, not cosmetic churn.

The sanctioned remedy is a `// cui-rewrite:disable CuiLoggerStandardsRecipe` at the affected site
with a comment citing the upstream defect `cuioss/cui-open-rewrite#135` — the form both live sites
already use (`JfrVarianceAnalyzer.printSummary()` and `TokenValidatorMetricsTest`). A format string
carrying `%n` must **never** be "fixed" by accepting the rewrite.

#### What is known about suppression placement, and what is not

The failure that produced the narrow-not-suppress rule was observed for a `// cui-rewrite:disable`
comment placed at the **closing-brace / `catch`** position, and only there. Class-level and
method-level suppression of `InvalidExceptionUsageRecipe` were never tested, so they are
**untested, not refuted**. Treat that as a known gap for a future investigation, not as a finding.

Adjacent evidence exists but must not be over-read: this tree relies on **method-level**
suppression of a *different* recipe (`CuiLoggerStandardsRecipe`, at the two `%n` sites), and a
`rewrite:dryRun` against the clean tree confirmed those suppressions do take effect at that
placement. That result speaks to the **placement mechanism** only. It says nothing about whether
`InvalidExceptionUsageRecipe` honours the same placements.

#### `-Ppre-commit` auto-fixes

`-Ppre-commit` rewrites your files. That is intended: the quality gate auto-fixes, in every
language and every cuioss repo. Review what it changed and commit it.

The profile inherited from `cui-java-parent` binds two mutating executions —
`format-license-headers` (`license:format`, phase `process-sources`) and `rewrite` (`rewrite:run`,
no declared phase, so the descriptor default `process-test-classes`). Both run ahead of `verify`.
Strip a license header, run the gate, and it exits **zero** with the header silently restored and
`git status` clean.

This repo previously inverted that, overriding both executions by id with `<phase>none</phase>` and
adding `license:check` plus `rewrite:dryRun(failOnDryRunResults=true)` to make the profile a
non-mutating verify gate. That inversion has been removed — it contradicted the org-wide decision
that the gate auto-fixes, and it was fragile: the override worked only by matching the parent's
execution ids, so a parent restructure that renamed either id would have silently restored the
mutations with no signal here.

What remains in the local `pre-commit` profile is unrelated to formatting: a parent-drift guard
(`dump-effective-pom-for-recipe-audit` + `assert-javautilapis-excluded`, both
`<inherited>false</inherited>`) asserting the parent has not silently re-activated
`org.openrewrite.java.migrate.util.JavaUtilAPIs`, whose transitive `UseMapOf` recipe emits
non-compiling output (see PR #577). Keep it.

The gate still fails on things it cannot fix — `check-javadocs-pre-commit` runs with
`failOnError=true`/`doclint=html`. What it has no longer is a *formatting* assertion. Never
redeclare `activeRecipes` locally: the parent's recipe list is the single source of truth.

## Pre-1.0 Project Rules

This project is PRE-1.0 (see the root `pom.xml` for the current version). Therefore:
- **Never deprecate code** - Remove it directly if not needed
- **Never add transitional comments** like "TODO: Remove in v2.0"
- **Never enforce backward compatibility** - Make breaking changes freely
- **Never add @Deprecated annotations** - Delete unnecessary code immediately
- **Clean APIs aggressively** - Remove unused methods, classes, and patterns
- **Focus on final API design** - Design for post-1.0 stability

## Custom Commands

This project includes custom commands for common workflows:

### verifyCuiLoggingGuidelines
Comprehensive logging standards audit:
1. Analyze CUI logging standards from `/Users/oliver/git/cui-llm-rules/standards/logging`
2. Scan for logging violations in token-sheriff-validation module
3. Check LogRecord compliance
4. Validate documentation in `doc/LogMessages.adoc`
5. Run logging-related tests
6. Generate compliance report

### fixOpenRewriteMarkers <module-path>
Fix all OpenRewrite TODO markers in a module:
1. Locate all markers with grep
2. Analyze and fix each marker (production vs test code)
3. Remove markers after fixing
4. Verify fixes with pre-commit build
5. Final validation with full test suite

### verifyAndCommit <module-name>
Execute comprehensive quality verification and commit workflow for a specific module:
1. Quality verification build (pre-commit profile)
2. Final verification build (full integration)
3. Error resolution loop
4. Artifact cleanup verification
5. Git commit

## Skills

The project includes custom skills in `.claude/skills/`:

- `run-benchmark-suite` - Run full benchmark suite with ablation sweep, connection sweep, JFR profiling, and doc updates

## Documentation Standards

- **Format**: AsciiDoc with `.adoc` extension
- **Key documents**:
  - `README.adoc` - Project overview
  - `doc/README.adoc` - Documentation hub
  - `doc/validation/requirements.adoc` - Functional requirements
  - `doc/validation/architecture.adoc` - Architecture reference
  - `doc/LogMessages.adoc` - Logging reference
- **Cross-references**: Use `xref:` syntax (not `<<>>`)
- **Blank lines**: Required before all lists
- **Header**: Include TOC and section numbering
- **Source highlighting**: Use `:source-highlighter: highlight.js`
- **Package and ArchUnit patterns**: Write them with literal periods, exactly as they appear in the
  test sources — a documented pattern must be copy-pasteable into an ArchUnit rule without editing.
  Never substitute the Unicode ellipsis character (U+2026) for the two-period ArchUnit wildcard, and
  never use it to elide a package prefix: it renders as one glyph where the rule needs two periods,
  so a reader who copies it gets a pattern that silently matches nothing. `DocumentedPackagePatternTest`
  enforces this and fails the build when the glyph reappears in a documented package pattern.

### Javadoc Standards
- Every public and protected class/interface must be documented
- Include clear purpose statement in class documentation
- Document all public methods with parameters, returns, and exceptions
- Include `@since` tag with version information
- Document thread-safety considerations
- Include usage examples for complex classes and methods
- Every package must have package-info.java
- Use `{@link}` for references to classes, methods, and fields

## CDI and Quarkus Standards

- Use constructor injection (mandatory over field injection)
- Single constructor rule: No `@Inject` needed for single constructors
- Use `final` fields for injected dependencies
- Use `@ApplicationScoped` for stateless services
- Use `@QuarkusTest` for CDI context testing
- Use `@QuarkusIntegrationTest` for packaged app testing
- HTTPS required for all integration tests

## General Process Rules

1. **Use `.plan/temp/` for ALL temporary files** - Covered by `Write(.plan/**)` permission (avoids permission prompts)
2. **If in doubt, ask the user** - Never make assumptions
3. **Always research topics** - Use available tools (WebSearch, WebFetch, etc.) to find recent best practices
4. **Never guess or be creative** - If you cannot find best practices, ask the user
5. **Do not proliferate documents** - Always use context-relevant documents, never create without user approval
6. **Never add dependencies without approval** - Always ask before adding any dependency

## Important Files

Key reference files for development:
- `doc/LogMessages.adoc` - Complete logging reference
- `doc/README.adoc` - Documentation hub
- `doc/validation/requirements.adoc` - Functional requirements
- `doc/validation/architecture.adoc` - Architecture reference
- `.editorconfig` - Code formatting configuration
- `lombok.config` - Lombok configuration
