envelope_version=1
sender_type=plan
sender_id=plan-03-client-key-material-config-validators
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-17T14:56:54Z

component=pm-dev-java:junit-core
category=anti-pattern
status=active

# A test that mirrors an enumeration must derive its cases from that enumeration, and a refusal assertion must use an input the policy would otherwise admit

Two reviewer findings in one PR had the same cause: a test restated a set instead of reading it, or asserted a refusal on an input that was never going to be admitted.

## What happened

In plan plan-03-client-key-material-config-validators (PR #915), CodeRabbit flagged three test defects, all fixed:

- `PemKeyPairLoaderTest` kept a hand-written three-entry list of key types, so a new `KeyPairPolicy.KeyType` would not have failed any of the three parameterised loading tests. The cases are now generated from `KeyType.values()` with an exhaustive switch mapping each type to its fixture.
- `DpopProofGeneratorJktGoldenTest` repeated the six algorithms of `DpopProofGenerator.SUPPORTED_ALGORITHMS`. The cases now iterate that set, select the fixture by key type, and fail when an algorithm has no fixture.
- `BackChannelEgressPolicyTest` asserted case-insensitive allow-list matching with `2001:db8::1`, an address `EgressPolicy.check` admits anyway, so the assertion passed even if matching broke. It now uses a blocked address (`fd00::abcd`) and also asserts the same URL is refused without the entry.

## Rule

For any production enumeration (enum, supported-algorithm set, registry), derive test cases from it at run time and fail on a member with no fixture. For any "this entry makes the URL pass" assertion, first prove the URL fails without the entry.
