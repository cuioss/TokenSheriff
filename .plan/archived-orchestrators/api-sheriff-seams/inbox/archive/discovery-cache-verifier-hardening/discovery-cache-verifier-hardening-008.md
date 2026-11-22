envelope_version=1
sender_type=plan
sender_id=discovery-cache-verifier-hardening
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-16T22:25:34Z

# Candidate lesson: a single-flight winner that hands a superseding state to its joiners can give them an expired entry

Source: pr-comment finding f77d3b (PR #912, coderabbitai, CachingDiscoveryResolver.java:344), resolution fixed (TASK-12, regression test plus fresh-entry control TASK-13).

## Observation

The defect existed before this PR and surfaced only through review. With TTL=1s, caller A reads
`INITIAL`, pauses, and claims after caller B has already resolved and B's entry has expired. Caller C
joins A's claim before A re-reads the state. A sees a newer state than the one it read and completed
the claim with that state. C then called `answer()` on it without a freshness check and received the
expired `RESOLVED` entry. A itself re-evaluated, but its joiners did not.

## Fix

A superseded claim is now completed as aborted, so every joined caller re-evaluates against the
current state, the same way the winner already did. The regression test covers the joined-caller
scenario and was shown to fail on the old behaviour, with a fresh-entry control next to it.

## Candidate rule

In single-flight code, anything the winner gives to joiners must pass the same freshness predicate
the winner applies to itself. "A different state identity" does not prove "fresh". Check each guard's
predicate against the scenario the guard exists for, including the joiner's view and not only the
winner's.
