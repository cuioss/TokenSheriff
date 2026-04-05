# Output Examples

Examples of well-written findings and correctly dismissed false positives. Use these as a calibration reference when writing findings.

## Good Finding: Verified, Cross-Module, Concrete

> **M-3** | `BearerTokenResult.java:142-150` | `constraintViolation()` static factory method has zero production callers (searched `core`, `quarkus`, `deployment`, and `integration-tests` modules). `BearerTokenProducer` builds `CONSTRAINT_VIOLATION` results via the builder directly. | Remove until a consumer actually needs it.

Why this works: The "zero callers" claim was verified across all modules, the location is specific, and the recommendation is actionable.

## Good Finding: Duplication

> **D-1** | `TokenParser.java:45-67` + `HeaderParser.java:23-48` | Both methods implement the same "extract-and-validate" algorithm: split on delimiter, trim whitespace, validate format, return Optional. They differ only in delimiter character and validation regex. CPD does not flag this because variable names and types differ. | Extract shared parsing logic into a parameterized utility method accepting delimiter and validation pattern.

Why this works: Both locations are specified, the structural equivalence is explained, CPD non-detection is noted, and the fix is concrete.

## Good Finding: Cross-Module Duplication

> **D-4** | `core/util/StringHelper.java:12-28` + `quarkus/util/Strings.java:8-22` | Both modules implement identical `nullToEmpty()` and `isBlank()` utility methods. `git blame` confirms the quarkus version was copied from core 6 months ago. | Remove `quarkus/util/Strings.java` and depend on core's `StringHelper`.

Why this works: Origin confirmed via git blame, both locations specified, consolidation direction is clear.

## False Positive: Cross-Module Caller Missed

> ~~**M-1** | `SecurityEventCounter.java:181` | `reset()` is public but has zero production callers.~~ **Dismissed**: `JwtMetricsCollector.clear()` in the `quarkus` module calls `reset()`. The per-module analysis agent only searched `core` and missed this cross-module caller.

Lesson: Always search ALL modules before claiming "zero callers."

## False Positive: Invalid Attack Scenario

> ~~**C-1** | `DpopConfig.java:64` | `required` defaults to `false`, allowing attackers to strip `cnf.jkt` and bypass proof-of-possession.~~ **Dismissed**: `cnf.jkt` is inside the signed JWT access token — an attacker cannot strip it without breaking the signature. The `required=false` default is RFC 9449 compliant (dual bearer/DPoP mode).

Lesson: Values inside signed structures cannot be tampered with. Check the relevant RFC before flagging defaults as lenient.

## False Positive: Intentional Duplication

> ~~**D-2** | `InternalModel.java` + `ExternalDto.java` | Parallel hierarchies with 90% identical fields.~~ **Dismissed**: These classes sit on opposite sides of an anti-corruption layer. The duplication is intentional — the internal model must be free to evolve without breaking the external API contract.

Lesson: Parallel hierarchies across architectural boundaries are often intentional. Check whether the boundary is meaningful before flagging.

## False Positive: Spec-Compliant Behavior

> ~~**C-3** | `TokenValidator.java:89` | Accepts tokens with `typ: at+jwt` and `typ: JWT` — lenient parsing of the type claim.~~ **Dismissed**: RFC 9068 Section 2.1 specifies `at+jwt` but notes that implementations SHOULD accept `JWT` for backward compatibility. This is spec-compliant behavior.

Lesson: Always check the relevant specification before classifying flexible parsing as a vulnerability.
