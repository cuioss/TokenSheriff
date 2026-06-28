---
name: semantic-code-analysis
description: Semantic analysis of production code to find security issues, API design problems, dead code, unnecessary complexity, and semantic duplication. Use this skill whenever the user asks for a pre-release audit, API surface review, code cleanup, dead code detection, duplication analysis, security audit, or wants to reduce unnecessary complexity — even if they phrase it differently. Also use for code quality reviews, pre-release checklists, or "what can we clean up" requests.
user-invocable: true
---

# Semantic Code Analysis

Deep analysis of production code asking one question per artifact: _"Does this artifact earn its place in the codebase?"_

This goes beyond what automated tools (SpotBugs, PMD, CPD, SonarQube) catch. Those tools find syntactic issues — this analysis finds semantic problems: code that compiles and passes tests but represents the wrong design, unnecessary complexity, or hidden duplication.

## Parameters

- `scope`: What to analyze. One of `all` (default), a module path (e.g., `token-sheriff-validation`), or a package path.
- `maturity`: Project maturity context. One of `pre-1.0` (default, breaking changes are free), `stable` (breaking changes need deprecation), `frozen` (API locked, document only).
- `output`: Output file path (default: `doc/code-findings.adoc`).
- `include-tests`: Whether to analyze test code for duplication. One of `true`, `false` (default).
- `dimensions`: Which analysis dimensions to run. One of `all` (default), `security`, `api`, `dead-code`, `duplication`, `cleanup`. Comma-separated for multiple (e.g., `security,duplication`).

Parameters can be passed as positional arguments (`/semantic-code-analysis <scope> <maturity>`) or specified in natural language.

## Analysis Methodology

### Scope and Progress

When `scope=all`, the analysis covers every production module. To keep analysis tractable:
- For multi-module projects, launch parallel analysis agents (one per module). Each agent receives the loaded pattern catalogs and reports findings independently. The main agent then merges results and runs Phase 5.
- For projects with more than ~10 modules, prioritize modules that define the public API surface. Internal/utility modules can be analyzed in a second pass.
- After completing each module, provide a brief progress update.
- If the user wants to stop early, findings so far are still valid.

### Phase 1: Inventory and Reference Loading

1. Identify all production source files in scope (exclude test code, generated code, build artifacts).
2. Group by module/package for systematic coverage.
3. Detect the project's language, framework, and security model.
4. **Load pattern catalogs** based on project type and requested dimensions:

   **Always load** (unless `dimensions` excludes all their categories):
   - `references/structural-patterns.md` — security, API/design, dead code, and cleanup patterns
   - `references/duplication-patterns.md` — semantic duplication patterns for production code

   **Conditional loading**:
   - If `include-tests=true`, also load `references/test-duplication-patterns.md`

   For finding examples and calibration, consult `references/output-examples.md`.

5. If `dimensions` is not `all`, filter the loaded catalogs to only the requested categories.

### Phase 2: Semantic Scan

For each production artifact (class, method, field, constant), evaluate against the loaded pattern catalogs. The catalogs define what to look for and how to verify each pattern.

**Individual artifact scan**: Apply structural patterns from the catalogs — security, API/design, dead code, and cleanup patterns as applicable.

**Cross-artifact duplication scan**: After scanning individual artifacts within a module, compare across artifacts for semantic duplication. Apply the patterns from `references/duplication-patterns.md`. This is not token matching — look for structurally equivalent logic with different names, types, or minor control flow variations.

**Cross-module duplication scan** (when `scope=all`): After all modules are scanned, compare across module boundaries for utility reimplementation, model duplication, and repeated conversion logic.

**Test duplication scan** (when `include-tests=true`): Apply patterns from `references/test-duplication-patterns.md` to test source roots.

### Phase 3: Inline Verification

Quick sanity check during the scan. For each candidate finding, confirm it by reading the referenced code within the current module. Phase 5 performs thorough cross-module verification.

1. **Read the actual code** at the identified location to confirm the issue.
2. **Check for callers within the current module** — verify "unused" claims by searching production code.
3. **Check for tests** — note existing test coverage.
4. **Assess severity** based on actual impact, not theoretical risk.
5. **For duplication findings**: confirm both copies exist and serve the same semantic purpose. Check whether the similarity is structural (same algorithm) or superficial (same shape but different semantics).

### Phase 4: Classification

Classify each finding by severity:

- **Critical**: Security impact — fail-open, silent fallbacks, lenient defaults in security paths. Also: duplication in security-critical code paths where a fix in one copy might miss the other.
- **High**: API/design impact — affects the public API contract, creates false assumptions.
- **Medium**: Dead code, unnecessary API surface, semantic duplication in production code.
- **Low**: Minor cleanup, design inconsistencies, test duplication.

### Phase 5: False-Positive Review

This phase runs as a single pass by the main agent (not delegated to subagents), because it requires cross-module context.

After writing all findings to the output document, perform a dedicated verification pass. For **every** finding:

1. **Re-read the actual code** at the referenced location — do not rely on the earlier analysis.
2. **Search for callers across ALL modules** (not just the analyzed module). A method reported as "zero callers" in `core` may be called from `quarkus` or another module.
3. **Verify attack scenarios**: For security findings, confirm the described attack is mechanically possible. Check whether values are inside signed structures that prevent tampering.
4. **Verify naming claims**: If a finding says a name is "misleading," trace the complete code path.
5. **Check RFC/spec compliance**: For protocol-related findings, verify behavior matches the relevant specification.
6. **Verify duplication findings**: Confirm the duplication is semantic (same purpose), not just structural (same shape). Check if CPD/SonarQube already flags the pair — if so, remove the finding. Check if the duplication is intentional (anti-corruption layers, strategy pattern).
7. **Check deployment/build modules**: Verify that types recommended for deletion are not referenced by deployment processors, annotation processors, or `module-info.java`.
8. **Remove confirmed false positives** from the document and update the summary counts.

## Output Format

Generate an AsciiDoc file at the configured output path:

```asciidoc
= Semantic Code Analysis Findings
:toc: left
:toclevels: 3
:sectnums:

== Overview

Semantic analysis of production code — identifying security issues, design problems, dead code, unnecessary complexity, and semantic duplication that automated tools miss.

Maturity: {maturity} — {maturity-implications}
Dimensions: {dimensions}
Date: {date}

== Critical (Security Impact)

[cols="1,3,4,3"]
|===
|ID |Location |Issue |Recommendation

|C-1
|`ClassName:line-range`
|Description with specific code references.
|Concrete recommendation.
|===

== High (API / Design)
{same table format}

== Medium (Dead Code / Duplication)
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

Total: N findings across {scope}.
```

For duplication findings, the Location column should reference both locations: `ClassA.java:lines` + `ClassB.java:lines`.

## Maturity Implications

- **pre-1.0**: No deprecation, no legacy support, no backward compatibility. Breaking changes are free. Recommend removing/fixing everything.
- **stable**: Breaking changes need deprecation cycle. Fix Critical/High immediately, deprecate Medium, document Low.
- **frozen**: API locked. Document all findings. Only fix Critical security issues with backward-compatible changes.

## Rules

- **Semantic, not keyword**: Do not grep for "legacy", "compat", "TODO". Read the code and understand intent.
- **One question per artifact**: For every class, method, field — ask: does this earn its place?
- **Concrete locations**: Every finding must reference `ClassName:line-range`.
- **Concrete recommendations**: Every finding must have a specific, actionable fix.
- **No false positives**: Only report findings confirmed by reading the code. Consult `references/output-examples.md` for calibration.
- **Production code only** (unless `include-tests=true`): Do not analyze test code, generated code, or build scripts by default.
- **Exclude framework boilerplate**: Do not flag standard framework patterns unless they contain actual issues.
- **Semantic duplication only**: Do not flag duplication that CPD or SonarQube would catch. Focus on structurally equivalent code with different names, types, or control flow.
- **Test code only when opted in**: Do not analyze test code for duplication unless `include-tests=true`.
- **Check deployment/build modules**: Before recommending deletion, verify types are not referenced by build/deployment modules.
- **Verify naming claims**: Trace the complete code path before claiming a name is misleading.
- **Respect design decisions**: If a pattern has an explicit design decision comment, evaluate whether the rationale is still valid rather than flagging it.
