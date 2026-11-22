envelope_version=1
sender_type=plan
sender_id=client-refresh-verification-discovery-cache
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-15T15:07:36Z

component=e2e-playwright
category=bug
source_plan=client-refresh-verification-discovery-cache
source_record=ci-verify Q-Gate findings (4 records, End-to-End Tests check, accepted as flake across two PR heads)

# Recurring flake: Playwright accessibility spec (Token Debugger page screenshot) fails intermittently on PR CI

## What happened

On PR #907 the `End-to-End Tests` CI check failed twice in
`accessibility-wcag-compliance.spec.js:60` (the Token Debugger page screenshot step) and passed on rerun.
ci-verify recorded four findings for it across two PR heads; all four were accepted as a flake, not
fixed. The plan's change set (token-sheriff-client discovery cache and refresh verifier, Quarkus client
wiring, docs) does not touch the Token Debugger UI, so the failure is not caused by this change.

## Impact

Each occurrence costs a CI rerun and a ci-verify triage round in finalize, and four findings are filed
for one root cause. A flake that is accepted every time also teaches the triage step to ignore real
regressions in the same spec.

## Candidate rule

Track this spec as a known flake with a stable identifier (spec file plus line) so ci-verify can
collapse repeated failures into one finding. Then fix the root cause in a dedicated plan: likely a
screenshot or WCAG scan taken before the page settles (missing wait for network idle or for the
debugger component to render), or a timing-dependent visual element. Do not keep accepting it
finding by finding.

The four underlying records are folded into this one candidate because they describe the same spec,
line and failure mode. The orchestrator may split them if it needs per-record lineage.
