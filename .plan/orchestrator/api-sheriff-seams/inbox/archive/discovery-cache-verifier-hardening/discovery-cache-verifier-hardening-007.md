envelope_version=1
sender_type=plan
sender_id=discovery-cache-verifier-hardening
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-16T22:25:24Z

# Candidate lesson: hand-maintained setting registries that mirror validator parameter names drift silently

Source: pr-comment finding e1de7d (PR #912, coderabbitai, ClientConfigMapper.java:136), resolution fixed (TASK-9, tests TASK-11).

## Observation

`ClientConfigMapper` kept the four discovery-cache properties in `DISCOVERY_CACHE_SETTINGS` and,
separately, a map of parameter names that mirrored the strings used in
`CachingDiscoveryResolver.checkSettings`. The two agreed, but nothing kept them aligned. A setting
added to only one of them would skip its disabled-cache check or give a wildcard diagnostic instead
of naming the offending property.

## Fix

`checkSettings` now reports a structured `Setting` identity, and `ClientConfigMapper` derives both
registries from one exhaustive mapping. The message parsing and the wildcard fallback were removed.

## Candidate rule

When a config layer must name the property a lower-level validator rejected, the validator should
return a structured identity. Do not parse its message or keep a parallel string map. This matches
the repo path instruction that a hardcoded list mirroring a set defined elsewhere is a defect unless
it is derived from that set.
