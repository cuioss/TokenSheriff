# Epic: API Sheriff separation seams (0.9.10)

slug: api-sheriff-seams

> Hand-written narrative for one epic under `.plan/orchestrator/api-sheriff-seams/`. The layout and
> authority contract live in the central standard — see
> `persona-plan-orchestrator/standards/orchestration-model.md`. The ledger JSON files
> (`status.json`, the `queue/{PLAN-ID}.json` rows) and `resume_anchor.md` are the machine
> authority; any statement here that conflicts with them is stale prose.
>
> START HERE and the Ordered Queue are not in this file. They live in the generated,
> git-tracked `queue-view.md` next to it (see the Persist / Stop-Resume Contract in that
> standard). `queue-view.md` is never hand-edited. A merge conflict in it is never merged by
> hand: merge the source files, run
> `python3 .plan/execute-script.py plan-marshall:plan-orchestrator:orchestrator regenerate-view --slug api-sheriff-seams`
> on the merged tree, and `git add` the result.

## Vision

API Sheriff (the gateway, `/home/oliver/git/API-Sheriff`) must not duplicate functionality that
TokenSheriff provides, and generic functionality that lives in API Sheriff but belongs in a token
library must move here. A first duplicate (`BoundTokenEndpointClient`, 354 lines) was removed when
API Sheriff adopted 0.9.9. A follow-up audit (2026-11-12, four read-only reads of API Sheriff main
`26e34cd4` against this repository at 0.9.9 plus the release commit) found that the remaining
overlap is mostly a **missing or private TokenSheriff seam**: the code cannot be deleted on the
API Sheriff side until TokenSheriff publishes the seam. This epic ships those seams in **0.9.10**.
It is too large for one plan because it spans three modules (client, validation, the Quarkus
extensions) and mixes a security-sensitive change (refresh-result verification, the JWKS flood
bound) with small hygiene work. "Done" is: every seam below is released in 0.9.10 (or explicitly
declared out of scope by the operator), and API Sheriff's own plan PLAN-34 in epic
`kidicap-gateway-requirements` can delete the corresponding code against the released version.

**The consumer side is not in this epic.** API Sheriff deletes its copies in its own plan, after a
released 0.9.10 is pinned (the release workflow opens the bump PR). This epic only adds
TokenSheriff API. Everything new is additive and opt-in unless a spec states otherwise; the
fail-closed posture is never loosened.

## Queue annotations

- PLAN-01 — smallest and safest; run first. Closes a fail-open trap in native image for every
  TokenSheriff consumer, not only API Sheriff.
- PLAN-02, PLAN-03, PLAN-04 — all in `token-sheriff-client`; PLAN-02 and PLAN-04 both touch
  `RefreshFlow`. With `parallelization_scope` 1 they run in sequence, in the order 02, 03, 04.
- PLAN-04 — security-sensitive: a refreshed ID token is today accepted without OIDC Core 12.2
  checks on the consumer side because the verifier is private to `TokenLifecycleManager`.
- PLAN-05 — contains a back-channel logout-token deliverable that is **gated on an operator
  decision at outline** (TokenSheriff owns it, or declares it out of scope as the client docs do
  today: CLIENT-19).
- PLAN-06 — medium-high risk (a flood bound on an unauthenticated path); opt-in only.
- PLAN-07 — low priority; the operator may drop it at outline.

## Decisions

- 2026-11-12 — Epic created from the API Sheriff operator's request. Seven plans in two workstreams
  (WS-01 client seams: PLAN-01..04; WS-02 validation and shared-helper seams: PLAN-05..07), cut by
  module and by risk, not one plan per finding. `parallelization_scope` = 1 (the project default, not
  asked of the operator): most plans share `token-sheriff-client`. Target release 0.9.10 (the
  repository is at `0.9.10-SNAPSHOT`).
- 2026-11-12 — Not in this epic: anything API Sheriff can do with 0.9.9 as released (that is API
  Sheriff's PLAN-33); API Sheriff's own internal duplicates; `TokenLifecycleManager` as a replacement
  for the gateway's refresh coordinator (the manager is keyed on a server-side store and has no
  stateless cookie mode, ADR-0046 on the API Sheriff side). If the operator wants a typed-outcome
  single-flight primitive here later, it is a new plan.

## Open Defects

None recorded. Candidate defect from the audit, **not verified**: `RefreshFlow` (client) validates
only the access token of a refresh response (`RefreshFlow` ~:196), so a consumer that does not
use `TokenLifecycleManager` gets no ID-token or `sub` check. PLAN-04 settles it.

## Watches

- Source claims in the specs are audit leads read from code only; nothing was run. Each spec labels
  them OBSERVED or HYPOTHESIS with a verify-at-outline clause. — trigger: every plan's outline.
- API Sheriff's refresh persist is presence-only while 0.9.9 compare-and-sets on the base refresh
  token (reseed fix); a stale grant overwriting a re-login mid-refresh is hypothesised, not
  verified, and is API Sheriff's to settle (its PLAN-33 D5). — trigger: PLAN-04 outline, in case a
  TokenSheriff-side compare-and-set seam is the right answer.
- A TokenSheriff change in 0.9.10 can change API Sheriff behaviour on bump: the 0.9.9 notes list
  JWKS staleness bound and re-discovery (#844), `HttpJwksLoader` status truth (#830), issuer policy
  on cache hits (#833) and DPoP/step-up changes. — trigger: each landing here; the landing report
  names what API Sheriff must re-measure.
