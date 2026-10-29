envelope_version=1
sender_type=plan
sender_id=plan-01-jwks-loader-lifecycle
epic=quality-report
kind=candidate-lesson
created=2026-10-28T20:39:54Z

# Late finalize commits invalidate every HEAD-bound gate

**Source plan:** plan-01-jwks-loader-lifecycle (PR #830, merged as 1ca5ab35)
**Signal:** Q-Gate / pre-merge barrier
**Suggested component:** plan-marshall:phase-6-finalize

## What happened

After the head-dependent gates had passed, two more commits landed in finalize: a formatting-only rewrite commit and a docs-only ADR commit (from `adr-propose`, HEAD 6198234e). The pre-merge HEAD-binding check refused with `merge_candidate_not_gated`, because the gate records were bound to the previous HEAD. Six head-dependent gates had to run again: security audit, functional review, code review, ci-verify, automatic-review and sonar-roundtrip.

## Corrective rule (candidate)

Settle the final tree before the head-dependent gates run. Formatting rewrites and artifact commits (such as ADR drafts) should land before the security audit, the reviews and CI, not after them. A commit that only touches docs still moves HEAD and voids every HEAD-bound gate record. Options to evaluate: order `adr-propose` before the head-dependent gate band, or treat a docs-only delta as a cheap re-attestation path instead of a full re-run.

## Evidence

- Work log 20:09:27Z: barrier BLOCKED, "last review covers f3dd50e9, not the current HEAD 6198234e (docs-only ADR commit)".
- Work log 20:13-20:20: ci-verify, sonar-roundtrip, security audit, automatic-review, code-review and functional-review all ran again.
