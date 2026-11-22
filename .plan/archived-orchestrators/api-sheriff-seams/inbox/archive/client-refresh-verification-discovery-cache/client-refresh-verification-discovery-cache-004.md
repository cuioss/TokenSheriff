envelope_version=1
sender_type=plan
sender_id=client-refresh-verification-discovery-cache
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-15T15:07:48Z

component=token-sheriff-client-quarkus
category=anti-pattern
source_plan=client-refresh-verification-discovery-cache
source_record=pr-comment:ca6204 (CodeRabbit, PR #907, fixed in-run by TASK-011, commit 36b61921)

# Hand-kept list in a test mirrored the config declarations instead of deriving from them

## What happened

`TokenSheriffClientDiscoveryCacheTest` (token-sheriff-client-quarkus) held a hand-written list of the
discovery-cache setting keys for its orphan-property cases. The same set is declared in
`ClientRuntimeConfig` and copied a second time in `ClientConfigMapper.DISCOVERY_CACHE_SETTINGS`.
CodeRabbit flagged it: a new setting added to the config could be missing from both copies, so the
orphan check and its test would drift together and stay green. The fix (TASK-011, commit 36b61921)
derives the test cases from the `ClientRuntimeConfig` declarations. It cost a finalize loop-back to
5-execute and a full re-run of the finalize review and verify steps.

## Why it slipped

The repository's review path instructions already say to treat a hardcoded list that must mirror a set
defined elsewhere as a defect unless it is derived at build or run time. Neither the execute task nor
the finalize code-review applied that rule. Only the external review bot did.

## Candidate rule

When a test or guard enumerates members of a set that has an authoritative declaration (config
interface methods, enum constants, annotated fields), derive the enumeration from that declaration
by reflection or a generated source. Never keep a parallel literal list. The finalize code-review
should check for this explicitly, because the rule is already written in the project's review
instructions.
