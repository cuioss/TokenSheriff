# Test Duplication Pattern Catalog

Semantic duplication in test code that impacts maintainability. Loaded only when `include-tests=true`.

Not all test duplication is harmful — test readability sometimes benefits from explicit, self-contained setup. Flag duplication only when it creates a real maintenance burden: synchronized changes required across multiple test classes, or copy-paste bugs where one copy was updated but another was not.

## Setup Duplication

### Identical @BeforeEach / Setup Methods
Multiple test classes with the same setup logic: same mocks configured, same test data built, same services initialized. When the setup contract changes, all copies must be updated.

**What to check**: Compare setup methods across test classes in the same package. If 3+ classes share >80% identical setup, extraction into a shared fixture or JUnit extension is warranted.

### Repeated Mock Configuration
The same mock behavior (`when(...).thenReturn(...)`) configured identically across multiple test classes. Particularly costly when the mocked interface changes.

**What to check**: Search for identical mock setup sequences. If the same mock configuration appears in 3+ test classes, it should be a shared helper.

## Assertion Duplication

### Multi-Step Assertion Sequences
The same sequence of assertions (check status, extract body, verify fields, check headers) repeated across test methods. Should be a custom assertion or assertion helper.

**What to check**: Look for assertion sequences of 3+ steps that appear in multiple test methods. Single assertions (even if repeated) are fine.

### Custom Verification Logic
Hand-written verification logic (loops checking collections, string parsing to verify structure) that reimplements what a matcher or custom assertion could provide.

## Test Data Duplication

### Inline Constants and Payloads
Same test JSON payloads, token strings, or configuration values defined inline in multiple test classes instead of shared via constants or test data factories.

**What to check**: Search for identical string literals, JSON structures, or builder invocations across test classes. If the test data represents a domain concept (valid token, standard user), it should be defined once.

### Test Data Builder Duplication
Multiple test classes building the same test objects with identical field values instead of using a shared builder or factory method.

## Integration Test Duplication

### Container / Service Setup
Identical container configuration (`@QuarkusTestResource`, `@Testcontainers` setup) across multiple integration test classes. When the container config changes, all copies must be updated.

### HTTP Client Configuration
Repeated HTTP client setup (base URL, headers, auth tokens) across integration test classes instead of a shared test client.

## Verification

For each test duplication finding:
1. Confirm the duplication actually causes maintenance burden — has it led to bugs or synchronized edits?
2. Check whether extraction would harm test readability (self-contained tests are sometimes preferable)
3. Cross-module test duplication is more costly than within-module — prioritize accordingly
4. Do not flag test utility classes that exist specifically to reduce duplication
