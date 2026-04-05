# Structural Pattern Catalog

Patterns to evaluate during Phase 2. Each pattern includes what to look for, verification steps, and common false positives.

## Security Patterns (Critical)

These patterns create real attack surface. Verify each finding by confirming the described attack is mechanically possible.

### Fail-Open Behavior
Code that silently proceeds when validation or security checks fail. Look for catch blocks that swallow exceptions and return success, boolean methods that default to `true` on error, and guard clauses with missing `else` branches.

**Verification**: Trace the failure path end-to-end. If the failure results in a denied request downstream, the code is fail-closed despite appearances.

### Silent Fallbacks
Unknown or unexpected input silently mapped to a default instead of rejected. Particularly dangerous in `switch`/`match` statements handling security-relevant values (roles, permissions, token types).

**Verification**: Check whether the default is the most restrictive option. A fallback to "deny" is safe; a fallback to "allow" or "most common role" is not.

### Lenient Defaults
Security features disabled by default, requiring explicit opt-in. The risk is that deployments run with weaker security than intended.

**Verification**: Check the relevant RFC or specification. Many protocols legitimately define permissive defaults (e.g., RFC 9449 DPoP `required=false` for dual bearer/DPoP mode). Do not flag spec-compliant defaults.

### Compatibility Parsing
Accepting non-compliant input formats for interoperability. This widens the attack surface by allowing inputs the spec would reject.

**Verification**: Confirm the non-compliant input can carry a payload that compliant parsing would block. If the deviation is purely cosmetic (e.g., extra whitespace), the risk is negligible.

**Common false positive**: Values inside signed or encrypted structures (JWT claims, signed cookies) cannot be tampered with by an attacker. Do not report these as vulnerabilities.

## API / Design Patterns (High)

These affect the public API contract or create false assumptions about behavior.

### Dead Configuration
Fields that are configured, stored, and possibly documented but never enforced at runtime. The configuration creates a false sense of control.

**Verification**: Search all modules for reads of the configuration value that affect control flow (not just logging or display).

### False Security Assumptions
Configuration or API surface that suggests a security guarantee but has no enforcement. Example: a `maxRetries` field that is stored but never checked.

### Unnecessary Coupling
Core module depending on framework-specific types when a framework-agnostic alternative exists. Creates portability and testing barriers.

**Verification**: Check whether the framework type provides behavior the core module actually uses, or is merely passed through.

### Double Initialization
Redundant initialization paths for the same resource — blocking + async, eager + lazy, constructor + post-construct. One path is typically dead.

**Verification**: Confirm both paths can actually execute. If one is guarded by a condition that is always false in production, it is dead code rather than double init.

### Oversized Adapters
Large adapter or wrapper classes where production code uses a small subset of the adapted interface. The unused surface area is maintenance burden.

**Verification**: Count production callers for each adapted method. If >50% of methods have zero production callers, the adapter is oversized.

## Dead Code / Unnecessary API Surface (Medium)

### Unused Public Methods
Public methods with zero production callers across ALL modules. Being called only in tests does not justify public visibility.

**Verification**: Search all production source roots (not just the current module). Check deployment/build modules and `module-info.java` for reflective or declarative usage.

### Boolean Parameter Methods
APIs like `process(data, true)` where the boolean's meaning is opaque at the call site. Indicates the method is doing two different things.

### Magic Sentinels
Special "empty" or "null" instances using magic strings or constants (e.g., `EMPTY_TOKEN = new Token("")`). Prefer `Optional` or null with explicit documentation.

### Telescoping Constructors
Multiple constructors where only one is used in production. The others add API surface without value.

**Verification**: Check all modules for callers of each constructor variant.

### Null-Coalescing Constructors
Constructors that silently replace null parameters with defaults, hiding bugs at the call site. The caller may not realize their null was swallowed.

### Missing Input Validation
Builders or factories that accept invalid values (zero, negative, empty) without validation. The invalid state propagates until it causes a confusing failure elsewhere.

### Per-Call Object Creation
Only flag if the constructor performs parsing, allocation, or I/O. If it only stores references, the overhead is negligible — do not report micro-optimization opportunities.

## Cleanup Patterns (Low)

### Dual-Source Data
Same value derivable from two sources (constructor parameter + derived map entry). Creates a consistency risk if one source is updated without the other.

### Ignored Parameters
Method accepts a parameter but never reads it. May indicate an incomplete refactoring.

### Unnecessary Null Checks
Null-guarding values that are guaranteed non-null by the framework (dependency injection, constructor validation, `@NonNull` annotations).

**Verification**: Confirm the guarantee. CDI `@Inject` fields are non-null after construction. Constructor-validated fields depend on the validation actually running.

### Single-Value Enums
Enum with exactly one member, providing no polymorphism benefit. A constant or boolean may be clearer.

### Test-Only Public API
Public methods or constructors that exist solely for test convenience. Consider package-private visibility or test-specific builders.

### Inconsistent Patterns
Similar operations handled differently without documented reason. Example: some lookups null-checked, others not; some errors logged, others thrown.
