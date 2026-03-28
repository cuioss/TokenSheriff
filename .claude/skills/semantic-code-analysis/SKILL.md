---
name: semantic-code-analysis
description: Semantic analysis of production code to find compatibility shims, fail-open behavior, dead code, and unnecessary API surface. Use this skill whenever the user asks for a pre-release audit, API surface review, code cleanup before a release, dead code detection, security audit of fail-open behavior, or wants to reduce unnecessary complexity — even if they phrase it differently.
user-invocable: true
---

# Semantic Code Analysis

Deep analysis of all production code asking one question per artifact: _"Does this exist because it is the right design, or because we are afraid to break something?"_

**When to activate**: When the user wants a pre-release audit, API surface review, or wants to find compatibility workarounds, fail-open behavior, dead code, and unnecessary complexity.

## Parameters

- `scope`: What to analyze. One of `all` (default), a module path (e.g., `oauth-sheriff-core`), or a package path.
- `maturity`: Project maturity context. One of `pre-1.0` (default, breaking changes are free), `stable` (breaking changes need deprecation), `frozen` (API locked, document only).
- `output`: Output file path (default: `doc/code-findings.adoc`).

## Analysis Methodology

### Scope and Progress

When `scope=all`, the analysis covers every production module — this can be a large amount of code. To keep analysis tractable:
- Process one module/package at a time, reporting findings incrementally.
- After completing each module, provide a brief progress update before moving to the next.
- If the user wants to stop early, the findings so far are still valid and useful.

### Phase 1: Inventory

1. Identify all production source files in scope (exclude test code, generated code, build artifacts).
2. Group by module/package for systematic coverage.
3. Note the project's framework, dependencies, and security model.

### Phase 2: Semantic Scan

For each production artifact (class, method, field, constant), ask:

> Does this exist because it is the **right design**, or because we are **afraid to break something**?

Look specifically for these patterns:

#### Security (Critical)
- **Fail-open behavior**: Code that silently proceeds when validation/security checks fail
- **Silent fallbacks**: Unknown input silently mapped to a default instead of rejected
- **Lenient defaults**: Security features disabled by default requiring opt-in
- **Compatibility decompression/parsing**: Accepting non-compliant input formats for interoperability

#### API / Design (High)
- **Dead configuration**: Fields that are configured, stored, documented but never enforced
- **False security assumptions**: Config that gives a sense of security but has no effect
- **Unnecessary coupling**: Core module depending on framework-specific types
- **Double initialization**: Redundant init paths, blocking + async for the same operation
- **Oversized adapters**: Large adapter classes where production uses a tiny subset

#### Dead Code / Unnecessary API Surface (Medium)
- **Unused public methods**: Zero production callers, only called in tests
- **Boolean parameter methods**: Opaque `decode(token, true)` style APIs
- **Magic sentinels**: Special "empty" instances using magic strings
- **Telescoping constructors**: Multiple constructors where only one is used in production
- **Null-coalescing constructors**: Silently replacing null with defaults, hiding bugs
- **Missing input validation**: Builders accepting invalid values (zero, negative)

#### Cleanup (Low)
- **Dual-source data**: Same value derivable from two sources (constructor field + claims map)
- **Ignored parameters**: Method accepts a parameter but never uses it
- **Unnecessary null checks**: Null-guarding CDI-injected or framework-guaranteed non-null fields
- **Single-value enums**: Enum with exactly one value, providing no polymorphism benefit
- **Test-only public API**: Public methods/constructors that only tests call
- **Inconsistent patterns**: Some map lookups null-checked, others not

### Phase 3: Verification

For each finding:
1. **Read the actual code** at the identified location to confirm the issue
2. **Check for callers** — verify "unused" claims by searching production code
3. **Check for tests** — note existing test coverage
4. **Assess severity** based on actual impact, not theoretical risk

### Phase 4: Classification

Classify each finding into one of:
- **Critical**: Security impact — fail-open, silent fallbacks, lenient defaults in security paths
- **High**: API/design impact — affects the public API contract, creates false assumptions
- **Medium**: Dead code, unnecessary API surface, missing input validation
- **Low**: Minor cleanup, design inconsistencies, single-use abstractions

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

## Rules

- **Semantic, not keyword**: Do not grep for "legacy", "compat", "TODO". Read the code and understand intent.
- **One question per artifact**: For every class, method, field — ask the core question.
- **Concrete locations**: Every finding must reference `ClassName:line-range`.
- **Concrete recommendations**: Every finding must have a specific, actionable fix.
- **No false positives**: Only report findings where the issue is confirmed by reading the code.
- **Production code only**: Do not analyze test code, generated code, or build scripts.
- **Exclude framework boilerplate**: Do not flag standard framework patterns (CDI producers, JAX-RS resources) unless they contain actual issues.
