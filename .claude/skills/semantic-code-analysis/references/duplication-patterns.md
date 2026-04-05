# Semantic Duplication Pattern Catalog

Duplication that automated tools (CPD, SonarQube) cannot detect because the code differs syntactically but serves the same purpose. These tools match token sequences — this catalog targets structurally equivalent logic with different names, types, or control flow.

## When to Flag vs. When to Skip

Flag duplication when:
- A bug fix in one copy would require the same fix in the other
- The copies drift independently, creating subtle inconsistencies
- Extracting shared logic would reduce total code without harming readability

Skip duplication when:
- It crosses an anti-corruption layer boundary (intentional isolation)
- CPD or SonarQube already flags the same pair (no added value)
- The similarity is superficial — same structure but genuinely different semantics

## Production Code Patterns

### Parallel Hierarchies
Two class hierarchies that mirror each other: matching field sets, matching method signatures, matching validation logic. Common between request/response pairs, DTO/entity pairs, or internal/external model pairs.

**What to check**: Do both hierarchies always change together? If a field is added to one, must it be added to the other? If yes, this is mechanical duplication. If the hierarchies legitimately diverge (anti-corruption layer), it is intentional.

### Algorithm Duplication
The same algorithm implemented in multiple places with different variable names, types, or minor control flow variations. Common in validation logic, parsing routines, retry/backoff implementations, and error handling sequences.

**What to check**: Extract the algorithm's steps abstractly. If two methods follow the same sequence of operations (split, trim, validate, transform, return) differing only in parameters, they are duplicates. Compare by tracing the logic, not by visual similarity.

### Configuration Duplication
The same default value, constraint, or threshold defined in multiple places: in code constants and config files, across multiple config files, or in annotations and runtime checks.

**What to check**: Can the values drift independently? If changing one requires remembering to change the other, it is duplication. If they are intentionally independent (e.g., different environments), it is not.

### Adapter / Wrapper Duplication
Multiple adapters performing the same transformation with minor variations — different source types but identical mapping logic.

**What to check**: Could the adapters share a base implementation or be parameterized? If >70% of the mapping logic is identical, extraction is warranted.

### Builder / Factory Duplication
Multiple builders constructing similar objects with overlapping field sets. Often arises when a class evolves and new construction patterns are added without consolidating existing ones.

**What to check**: Compare field sets across builders. If overlap exceeds 70%, the builders should likely share construction logic.

## Cross-Module Duplication

### Utility Reimplementation
The same utility method (string manipulation, collection transformation, date formatting) implemented independently in multiple modules instead of placed in a shared module.

**What to check**: Search for methods with similar signatures and logic across module boundaries. Utility duplication is the most common and most wasteful form.

### Model Duplication
Model classes duplicated across module boundaries, often with slight field differences. Creates mapping boilerplate at every module boundary.

**What to check**: If the models are 90%+ identical and always mapped 1:1, consolidation is warranted. If they serve different bounded contexts, the duplication may be intentional.

### Conversion Logic Duplication
The same type conversion (e.g., string-to-enum, DTO-to-entity) implemented at multiple module boundaries instead of centralized.

## Verification Checklist

For each duplication finding:
1. Confirm both copies serve the same semantic purpose (not just structural resemblance)
2. Check `git blame`: did they originate from copy-paste or independent authoring?
3. Verify CPD/SonarQube does not already flag this pair — if it does, remove the finding
4. If the duplication appears intentional (anti-corruption layer, strategy pattern implementations that must diverge), document rather than flag
5. Assess the maintenance cost: how often do the copies need synchronized changes?
