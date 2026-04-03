---
name: semantic-code-analysis
description: Semantic analysis of production code to find compatibility shims, fail-open behavior, dead code, and unnecessary API surface. Use this skill whenever the user asks for a pre-release audit, API surface review, code cleanup before a release, dead code detection, security audit of fail-open behavior, or wants to reduce unnecessary complexity — even if they phrase it differently.
user-invocable: true
---

# Semantic Code Analysis

Deep analysis of all production code asking one question per artifact: _"Does this exist because it is the right design, or because we are afraid to break something?"_

## Parameters

- `scope`: What to analyze. One of `all` (default), a module path (e.g., `oauth-sheriff-core`), or a package path.
- `maturity`: Project maturity context. One of `pre-1.0` (default, breaking changes are free), `stable` (breaking changes need deprecation), `frozen` (API locked, document only).
- `output`: Output file path (default: `doc/code-findings.adoc`).

Parameters can be passed as positional arguments (`/semantic-code-analysis <scope> <maturity>`) or the user may specify them in natural language. Default to `scope=all`, `maturity=pre-1.0` if not specified.

## Analysis Methodology

### Scope and Progress

When `scope=all`, the analysis covers every production module — this can be a large amount of code. To keep analysis tractable:
- For multi-module projects, launch parallel analysis agents (one per module) to maximize throughput. Each agent receives the full pattern catalog and reports findings independently. The main agent then merges results and runs Phase 5.
- For projects with more than ~10 modules, prioritize modules that define the public API surface. Internal/utility modules can be analyzed in a second pass if the user wants full coverage.
- After completing each module, provide a brief progress update before moving to the next.
- If the user wants to stop early, the findings so far are still valid and useful.

### Phase 1: Inventory

1. Identify all production source files in scope (exclude test code, generated code, build artifacts).
2. Group by module/package for systematic coverage.
3. Note the project's framework, dependencies, and security model.

### Phase 2: Semantic Scan

For each production artifact (class, method, field, constant), ask:

> Does this exist because it is the **right design**, or because we are **afraid to break something**?

Look specifically for these patterns. The core patterns below apply to any language/framework. For Java/CDI/Quarkus projects, also consult `references/java-patterns.md` if it exists.

#### Security (Critical)
- **Fail-open behavior**: Code that silently proceeds when validation/security checks fail
- **Silent fallbacks**: Unknown input silently mapped to a default instead of rejected
- **Lenient defaults**: Security features disabled by default requiring opt-in
- **Compatibility parsing**: Accepting non-compliant input formats for interoperability

For each security finding, describe the specific attack vector and verify it is mechanically possible. If the value in question is inside a signed or encrypted structure (e.g., a JWT claim, a signed cookie), it cannot be tampered with by an attacker — do not report this as a vulnerability. Check whether the behavior matches the relevant RFC/spec before classifying a default as "lenient."

#### API / Design (High)
- **Dead configuration**: Fields that are configured, stored, documented but never enforced
- **False security assumptions**: Config that gives a sense of security but has no effect
- **Unnecessary coupling**: Core module depending on framework-specific types
- **Double initialization**: Redundant init paths, blocking + async for the same operation
- **Oversized adapters**: Large adapter/wrapper classes where production uses a tiny subset

#### Dead Code / Unnecessary API Surface (Medium)
- **Unused public methods**: Zero production callers across ALL modules (not just the current one), only called in tests
- **Boolean parameter methods**: Opaque `process(data, true)` style APIs where the boolean's meaning is unclear
- **Magic sentinels**: Special "empty" instances using magic strings or constants
- **Telescoping constructors**: Multiple constructors where only one is used in production
- **Null-coalescing constructors**: Silently replacing null with defaults, hiding bugs
- **Missing input validation**: Builders/factories accepting invalid values (zero, negative, empty)
- **Per-call object creation**: Only flag if the constructor performs parsing, allocation, or I/O. If it only stores references, the overhead is negligible — do not report it.

#### Cleanup (Low)
- **Dual-source data**: Same value derivable from two sources (constructor field + derived map)
- **Ignored parameters**: Method accepts a parameter but never uses it
- **Unnecessary null checks**: Null-guarding framework-guaranteed non-null fields (e.g., dependency-injected, constructor-validated)
- **Single-value enums**: Enum with exactly one value, providing no polymorphism benefit
- **Test-only public API**: Public methods/constructors that only tests call
- **Inconsistent patterns**: Similar operations handled differently without reason (some lookups null-checked, others not)

### Phase 3: Inline Verification

Quick sanity check during the scan — for each candidate finding, confirm it by reading the referenced code within the current module. Phase 5 performs the thorough cross-module verification after all findings are written.

1. **Read the actual code** at the identified location to confirm the issue
2. **Check for callers within the current module** — verify "unused" claims by searching production code
3. **Check for tests** — note existing test coverage
4. **Assess severity** based on actual impact, not theoretical risk

### Phase 4: Classification

Classify each finding into one of:
- **Critical**: Security impact — fail-open, silent fallbacks, lenient defaults in security paths
- **High**: API/design impact — affects the public API contract, creates false assumptions
- **Medium**: Dead code, unnecessary API surface, missing input validation
- **Low**: Minor cleanup, design inconsistencies, single-use abstractions

### Phase 5: False-Positive Review

This phase runs as a single pass by the main agent (not delegated to subagents), because it requires cross-module context that per-module agents don't have.

After writing all findings to the output document, perform a dedicated verification pass. For **every** finding in the document:

1. **Re-read the actual code** at the referenced location — do not rely on the earlier analysis pass.
2. **Search for callers across ALL modules** (not just the module being analyzed). A method reported as "zero production callers" in `core` may be called from `quarkus` or another module.
3. **Verify attack scenarios**: For security findings, confirm the described attack is actually possible. Check whether the value in question is inside a signed structure (JWT, etc.) that prevents tampering.
4. **Verify naming claims**: If a finding says a name is "misleading," re-read the code path to confirm the name doesn't accurately describe the behavior.
5. **Check RFC compliance**: For protocol-related findings, verify whether the current behavior matches the relevant RFC before recommending changes.
6. **Remove confirmed false positives** from the document and update the summary counts.

This phase exists because analysis agents explore one module at a time and can miss cross-module callers, mischaracterize RFC-compliant defaults as security issues, or flag lightweight patterns as wasteful.

## Output Format

Generate an AsciiDoc file at the configured output path with this structure:

```asciidoc
= Code Findings: Compatibility vs. Correct Design
:toc: left
:toclevels: 3
:sectnums:

== Overview

Semantic analysis of all production code asking one question per artifact:
_"Does this exist because it is the right design, or because we are afraid to break something?"_

Maturity: {maturity} — {maturity-implications}

Date: {date}

== Critical (Security Impact)

[cols="1,3,4,3"]
|===
|ID |Location |Issue |Recommendation

|C-1
|`ClassName:line-range`
|Description of the issue with specific code references.
|Concrete recommendation.
|===

== High (API / Design)
{same table format}

== Medium (Dead Code / Unnecessary API Surface)
{same table format}

== Low (Cleanup)
{same table format}

== Summary

[cols="1,1,1"]
|===
|Severity |Count |Key Theme
|Critical |N |theme
|High |N |theme
|Medium |N |theme
|Low |N |theme
|===

Total: N findings across all production code.
```

## Maturity Implications

- **pre-1.0**: No deprecation, no legacy support, no transitional code, no backward compatibility shims. Breaking changes are free. Recommend removing/fixing everything.
- **stable**: Breaking changes need deprecation cycle. Recommend fixing Critical/High immediately, deprecate Medium, document Low.
- **frozen**: API locked. Document all findings. Only fix Critical security issues with backward-compatible changes.

## Examples: Good Finding vs. False Positive

**Good finding** (verified, cross-module, concrete):

> **M-3** | `BearerTokenResult.java:142-150` | `constraintViolation()` static factory method has zero production callers (searched `core`, `quarkus`, `deployment`, and `integration-tests` modules). `BearerTokenProducer` builds `CONSTRAINT_VIOLATION` results via the builder directly. | Remove until a consumer actually needs it.

Why this is good: The "zero callers" claim was verified across all modules, the location is specific, and the recommendation is concrete.

**False positive** (dismissed during Phase 5):

> ~~**M-1** | `SecurityEventCounter.java:181` | `reset()` is public but has zero production callers.~~ **Dismissed**: `JwtMetricsCollector.clear()` in the `quarkus` module calls `reset()`. The per-module analysis agent only searched `core` and missed this cross-module caller.

Why this was wrong: The analysis agent only searched the module it was analyzing (`core`), missing the caller in a different module (`quarkus`).

**False positive** (invalid attack scenario):

> ~~**C-1** | `DpopConfig.java:64` | `required` defaults to `false`, allowing attackers to strip `cnf.jkt` and bypass proof-of-possession.~~ **Dismissed**: `cnf.jkt` is inside the signed JWT access token — an attacker cannot strip it without breaking the signature. The `required=false` default is RFC 9449 compliant (dual bearer/DPoP mode).

Why this was wrong: The described attack is physically impossible, and the behavior matches the RFC.

## Rules

- **Semantic, not keyword**: Do not grep for "legacy", "compat", "TODO". Read the code and understand intent.
- **One question per artifact**: For every class, method, field — ask the core question.
- **Concrete locations**: Every finding must reference `ClassName:line-range`.
- **Concrete recommendations**: Every finding must have a specific, actionable fix.
- **No false positives**: Only report findings where the issue is confirmed by reading the code.
- **Production code only**: Do not analyze test code, generated code, or build scripts.
- **Exclude framework boilerplate**: Do not flag standard framework patterns (e.g., dependency injection producers, REST resource classes, ORM mappings) unless they contain actual issues.
- **Check deployment/build modules**: When recommending deletion or visibility changes to production types, verify they are not referenced by build/deployment modules (e.g., Quarkus deployment processors, annotation processors, module-info.java). These modules are excluded from the analysis scope but may depend on production types.
- **Verify naming claims**: Before claiming a name is "misleading," trace the complete code path the name governs. If the name accurately describes what the code does, do not flag it.
- **Respect design decisions**: If a pattern has an explicit design decision comment (e.g., `// Design Decision: ...`), evaluate whether the rationale is still valid rather than flagging the pattern as a finding.
