# Landing Analysis: PLAN-18 — AsciiDoc accuracy, claims that contradict the code

epic: quality-report
workstream: WS-05
pr: #877 (merge `af19b9c2b675bb2f3b1d5ff1b5d28ea8a8af8325`, merged 2026-11-06T18:30:25Z)

> Landing record for one shipped plan. Written by the `analyze` verb (inbox scan) after verifying the landing message
> against git, the PR and Sonar. The inbox landing was `plan-18-doc-accuracy-008.md`; its `landing-check` reported
> `complete: false` (missing key `effort`, written as `unknown` by the producer), so the landing is reconciled as far as
> it goes and recorded as an Open Defect.

## Deliverable Fidelity vs Spec

Corroborated against the real merge diff (`af19b9c2`, 20 files, +134/-79), the PR state read through the CI
abstraction (state `merged`, 24 checks, none failing; CodeRabbit `NEUTRAL`, Sourcery `SUCCESS`) and the merged source.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. D-2 client back-channel ownership, five places | shipped-modified | All five spec locations (`doc/client/README.adoc`, `token-sheriff-client/README.adoc`, the client `package-info.java`, `doc/commons/README.adoc`, `doc/README.adoc`) now say commons owns validation-side discovery and JWKS and the client owns its back channel (`BackChannelHttp`). The same claim family recurred in nine more files, so the work spread to 20 files: the operator extended scope twice (the `<description>` of `token-sheriff-client/pom.xml`; `commons/transport/package-info.java`). `git grep "issues no HTTP"` now finds only the two intentional retractions (`doc/client/architecture.adoc`, `doc/client/requirements.adoc:56`). |
| 2. D-6 / V-17d `ParserConfig` limits and last-wins | shipped-as-specified | "128 KB buffer" removed from `security-reference.adoc` and `threat-model.adoc` (3 places); the real limits (token 8 KB, decoded part 8 KB, string 4 KB, depth 10, array 1000, HTTP response 256 KB) are labelled defaults. Duplicate-member last-wins is stated at `security-reference.adoc:61`. The "align with PLAN-13's response-size ceiling" clause was honoured (`ParserConfig.maxHttpResponseSize`). |
| 3. D-9 map CLIENT-21/23/24 in the client README | shipped-as-specified | CLIENT-21 and CLIENT-24 on the Architecture row (`doc/client/README.adoc:53`), CLIENT-23 on the retrieval-flow row (line 65); the xref anchors exist in `requirements.adoc`. |
| 4. D-13 Quarkus version caveat | shipped-modified (spec said DROPPED) | The spec dropped D-13 as already fixed, on the strength of `benchmarking/doc/Analysis-03.2026-JFR-Profiling.adoc:12`. The source report located the defect at `token-sheriff-quarkus-parent/doc/performance/jwt-validation-performance.adoc:18` and cited the benchmarking file only as the correct contrast. The lane re-instated it and the caveat is now at line 18 ("version at recording time (March 2026 baseline); project has since upgraded"). Record D-13 against #877, not against `4f8f3eb6`. |
| 5. D-14 introspection: endpoint parsed, no flow | shipped-as-specified | Three client docs plus the client `package-info.java` and the `ClientAuthentication` / `ClientAuthMethod` / `auth` Javadocs now say so. Verified in the source: introspection appears in client main only in `ProviderMetadata` and `DiscoveryResolver`; `validateEndpointSchemes` returns early when the issuer is not https, so the https check applies to https issuers only, which is how the docs now word it. |

Added-unplanned (accurate, no deliverable behind them): the `DiscoveryResolver` Javadoc sentence about `ParserConfig`, and the
"HTTPS-only" qualifiers on `sslContext` and verify-hostname across five files (accurate against `BackChannelHttp`).

## Three-Layer Analysis

- **Dispatch:** one read-only leaf per landing (`execution-context-level-3`, resolved on the `orchestrator.analyze` surface, source `plan.effort`) — reason: reading the merged claims against the implementing source and the spec is the depth the dispatch rule exists for, and this is the only landing in the drain. The leaf wrote nothing. The orchestrator re-read at `file:line` against `af19b9c2` / `main`: the diff stat and the non-comment lines of the Java and pom hunks (all six Java hunks are inside comments; the pom change is `<description>` text only), `jwt-validation-performance.adoc:16-19`, `source-report/documentation-review.adoc:174-180`, `findings-disposition.md:124`, `doc/commons/requirements.adoc:79-86`, `doc/commons/specification/transport.adoc:125-133`, `doc/commons/threat-model.adoc:41-49,123-126`, `DiscoveryResolver.java:44-56`, `doc/client/specification/test-strategy.adoc:127-132,233-235`, `doc/client/specification/step-up-and-logout.adoc:111-115`, `doc/client/requirements.adoc:206-210,517-520`, `doc/commons/README.adoc:22-26`, `doc/README.adoc:28-31`, `doc/client/README.adoc:28-32`, `git grep EgressPolicy` over main code (no client file uses it), and the client test imports (no client test imports `RevocationDispatcher`, `IntrospectionDispatcher` or `EndSessionDispatcher`; `RevocationClientTest.java:168` declares its own nested `RevocationDispatcher`). Not re-read, so recorded as leads: `BackChannelHttp.java` line numbers the leaf cites, the `ParserConfig` limit values and where `NonValidatingJwtParser` enforces them, `MapRepresentation` null filtering for the header half, and every statement about what a build or a render would show.
- **Verdict:** layer 1 findings (1 major, 1 minor, 2 info); layer 2 findings (2 minor, rest info); layer 3 findings (4 major, 6 minor, 1 not shown by reading).

### Layer 1 — Specified

| Finding | Claim | Verdict | Evidence (file:line) |
|---------|-------|---------|----------------------|
| L1-1 | The D-13 drop was mis-grounded: the re-grounding checked the contrast file, not the defect site, and the spec contradicts itself (Objective lists D-13 under "Closes" and Expected Surface lists the perf doc while deliverable 4 says DROPPED) | checked | `plans/PLAN-18-doc-accuracy.md:10-12,21,56-57,71`; `source-report/documentation-review.adoc:174-180`; the perf doc line 18 had the uncaveated figure until #877 |
| L1-2 | Expected Surface listed 11 paths; the diff touches 20 (all 11 were touched; 9 not declared) | checked | `git show --stat af19b9c2`; the gate under-declared the same claim family |
| L1-3 | The spec has no Done-when section and says "no code change" while Javadoc and the pom `<description>` are in scope | checked | section list of the spec; the comment-only reading holds at layer 2 |
| L1-4 | The spec cites two different SHAs for the same re-grounding (`aa77acd9` in Standing Constraints, `306e6053` in the claim labels) | checked | `plans/PLAN-18-doc-accuracy.md:26,49` |

PLAN-18 is shipped, so no staged spec carries these claims and `corpus set-verdict` persists nothing. The D-13 claim label (`contradicted`, "already fixed") is itself wrong and stays in the shipped spec as history. `findings-disposition.md` still read "D-13 VALID" and is reconciled at this analysis.

**Layer 1 verdict:** findings — 1 major (L1-1), 1 minor, 2 info

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| 1 (D-2) | the five spec locations plus nine more files | none (docs and comments; no test change) | `DiscoveryResolver.java:52-54` ParserConfig sentence; the HTTPS-only qualifiers in `doc/client/requirements.adoc`, `retrieval-flow.adoc`, `threat-model.adoc`, `transport.adoc`, `token-sheriff-client/README.adoc`. Both accurate against `BackChannelHttp`; scope drift, benign. | checked |
| 2 (D-6, V-17d) | `security-reference.adoc:59,61`, `threat-model.adoc:92,223` | none | none | checked |
| 3 (D-9) | `doc/client/README.adoc:53,65` | none | none | checked |
| 4 (D-13) | perf doc line 18; the project is on Quarkus 3.39.2 (`token-sheriff-quarkus-parent/pom.xml:46`), so "has since upgraded" is true | none | none | checked |
| 5 (D-14) | three client docs plus five Javadoc files | none | none | checked |
| lane report | "No behavioural change, no test change, no new or renumbered log identifier, anchor IDs untouched": no `src/test` path and no `LogMessages` file in the diff; the Java hunks are comments (re-read); the pom hunk is `<description>`. No `[#..]` or `[[..]]` line changed (leaf, `-U0` diff; not re-run by me). Every xref the PR added resolves (leaf). | n/a | n/a | checked |

**Layer 2 verdict:** findings — 2 minor (the unexplained hunks above), no missing trace

### Layer 3 — Correct

The docs-only diff was read as claims against the code. D-2, D-6, V-17d, D-9, D-13 and D-14 wording is accurate against `BackChannelHttp`, `ParserConfig` and `DiscoveryResolver`. The six advisory follow-ups the lane named are all real residuals at `main`, and four of them are factual contradictions of the code:

| Defect | File:line | Claim | Code fact | Severity |
|--------|-----------|-------|-----------|----------|
| L3-1 | `doc/commons/requirements.adoc:81-85` (COMMONS-3), `doc/commons/specification/transport.adoc:127-132`, `doc/commons/threat-model.adoc:43-48,125` | any IdP-advertised URL (`jwks_uri`, discovery, `userinfo`, any advertised endpoint) is host allow-listed by `EgressPolicy` and internal/link-local/loopback ranges are blocked | `EgressPolicy` is applied only in `HttpWellKnownResolver` and `HttpJwksLoader` (`git grep EgressPolicy` over main code, no client file). The client back channel (userinfo, token, PAR, revocation) validates path structure and scheme only; `doc/client/architecture.adoc` says it "is not a host-address SSRF allow-list", and the PR's own new `doc/commons/README.adoc:24-27` and `transport.adoc:19-21` say the same. The docs promise more than the code does. | major |
| L3-2 | `DiscoveryResolver.java:45-48` | "the same TLS/SSRF-hardened transport primitive the commons layer uses ... adds no new transport control of its own; the security posture is inherited from HttpHandler" | the resolver goes through `BackChannelHttp`, which adds egress path validation, a scheme check, the back-channel origin rewrite and a bounded body read, plus the https-issuer endpoint-scheme refusal and a Content-Type check in the resolver itself; and "SSRF-hardened" conflicts with L3-1. The contradiction sits two lines above a hunk this PR edited (lines 52-54). | major |
| L3-3 | `doc/client/specification/test-strategy.adoc:128-132,233-235` | the client tests reuse `RevocationDispatcher`, `IntrospectionDispatcher` and `EndSessionDispatcher`; "`EndSessionDispatcher` returns an open-redirect ... rejected" | no client test imports those shared dispatchers (re-read: the only match is `RevocationClientTest.java:168`, its own nested class of the same name); the engine makes no end-session HTTP call (`EndSessionFlow` builds the redirect) | major |
| L3-4 | `doc/client/specification/step-up-and-logout.adoc:112-114` | "The commons `EndSessionDispatcher` adversarial mode returns open-redirect ... responses, which the engine rejects" | same as L3-3 | major |
| L3-5 | `doc/commons/specification/transport.adoc:45-47,178-180`, `doc/client/requirements.adoc:519` | the revocation dispatcher is driven by the client engine's own tests (new text in this PR, which replaced "end-session" with "revocation") | true for token, userinfo and PAR; false for the shared revocation dispatcher (`RevocationClientTest` uses its own nested class) | minor |
| L3-6 | `doc/commons/specification/transport.adoc:53-55`, `doc/commons/requirements.adoc:51-53` (COMMONS-2) | "Every outbound request uses TLS 1.2+ ... Plain-HTTP endpoints are rejected" | cleartext is admitted on explicit opt-in on both sides; the PR's own `transport.adoc:64-65` says a cleartext target carries no TLS | minor |
| L3-7 | `token-sheriff-client/README.adoc:30-31` | "per-client TLS trust seam shared by every discovery and back-channel call" | the same claim was qualified to "every HTTPS ..." at lines 84-87 and left here; `BackChannelHttp` skips the `SSLContext` for cleartext targets | minor |
| L3-8 | `doc/commons/README.adoc:22-23`, `doc/README.adoc:30`, `doc/commons/architecture.adoc`, `doc/client/README.adoc:29-31`, the client pom `<description>` | "shared transport primitives: TLS enforcement, DoS bounds, resilience and caching" / "shared TLS / SSRF / resilience primitives"; the client "delegates" the `cui-http` handler, `de.cuioss.http.security` and discovery to commons | no retry/backoff or discovery caching in client main (leaf, `git grep`), `EgressPolicy` is validation-side only (as `commons/README.adoc:24-25` itself says), `cui-http` belongs to the upstream library, and `DiscoveryResolver` says the commons well-known resolver is not used. The hub line is internally inconsistent with `commons/README.adoc`. Overstated, not false per item. | minor |
| L3-9 | `commons/transport/package-info.java:35-37` | unchanged paragraph keeps "SSRF defence" as the package's job | correct for the validation side only; the new text does not say the allow-list is not applied to the client | minor |
| L3-10 | `doc/validation/security-reference.adoc:61` | "a numeric `alg` ahead of a string `alg` can fail deserialization" (header half of the duplicate-key sentence) | depends on the generated DSL-JSON converter for `JwtHeader` and on DSL-JSON 2.0.2; the converter is not in the repository and no test covers duplicate JWT members | not_shown_by_reading |

**Layer 3 not shown by reading:** the asciidoctor render, the Javadoc build and a link check; the header duplicate-key behaviour (L3-10); that a caller-supplied `SSLContext` with client keys lets an https peer receive a certificate (standard JSSE behaviour).

**Layer 3 verdict:** findings — 4 major (L3-1 to L3-4, all documentation contradicting the code, none a code defect), 6 minor, 1 not shown by reading. Reported to the operator in the same turn. No fix is made inline. The standing "leftovers go into the one final plan" instruction applies: folded into PLAN-25 as item 7(j), with one operator question (L3-1: correct the docs to the code, or apply `EgressPolicy` to the client back channel, a behaviour change).

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| AGENTS.md | Use `xref:` syntax, not `<<>>` | deviates (minor) | the PR added `(<<CLIENT-23>>)` at `doc/client/requirements.adoc:208`, matching that file's existing `<<CLIENT-4>>` convention. PLAN-19 deliverable D-11 sweeps exactly these, so no new item. |
| AGENTS.md | Blank line before lists | conforms | the diff adds no list block (render not shown by reading) |
| AGENTS.md | No hard-coded versions | conforms | the only literal is the dated "Quarkus 3.34.0" now marked as recording-time |
| AGENTS.md | New log identifiers need `LogMessages.adoc` | conforms | no `LogMessages` file in the diff |
| AGENTS.md | Javadoc: `{@link}` for references, package-info per package | conforms | text-only edits; links resolve (leaf) |
| Spec constraints | Docs-only: verify each new statement against the implementing source | deviates | no code change, but the residual contradictions L3-1 to L3-4 and L3-5 were left or introduced |
| Spec constraints | Do not edit `source-report/` or `findings-disposition.md` | conforms | neither is in the diff |
| Spec constraints | PR title/body references every finding ID it closes | conforms | read directly: the PR body opens "Closes D-2, D-6, D-9, D-13, D-14, V-17d." |
| CLAUDE.md | Merge only after CI green and every AI-reviewer comment resolved | conforms | read directly: 24 checks, 0 failing (CodeRabbit `NEUTRAL`), Sonar below; comment resolution rests on the lane's own record |

## Metrics and Anomalies

- Tokens: 19,870,958 total (landing fact; two phase-6 usage figures of about 1,000 and 13,000 were entered as estimates, so the finalize total is a slight overstatement).
- Duration: 17,054 s wall (about 4 h 44 m).
- Anomalies: the plan looped back 4-plan to 3-outline to 4-plan to add the `commons/transport/package-info.java` hit that the 4-plan scope check found; the docs-only plan took three functional-review rounds and two code-review rounds, each finding one more neighbouring stale claim of the same class; functional review round 1 was closed by operator override over the criterion "D-13 is recorded against this PR in the PR body", which cannot be evidenced before a PR exists (a later round evidenced it); coderabbit's free-tier quota refusal stalled required-bot participation until the rate-window await was enabled; sourcery caught a new slipped claim (the `introspection_endpoint` https check applies only to https issuers), fixed in four places. `effort` is `unknown` (never stamped), `model` is self-reported, the landing is incomplete on that key only. The squash commit message on `main` carries the CodeRabbit "Summary by CodeRabbit" block and a second `Co-authored-by` trailer; the merge-queue path offers no per-enqueue message override.

## Routing and Merge Behavior

- Review: sourcery and coderabbit comments remediated in-run (`DiscoveryResolver.java` Javadoc only); code review 0 blocking, 6 advisory, which are the six residuals above.
- CI/merge: all PR checks green at merge, merged through the queue 2026-11-06T18:30:25Z. No rebase conflict reported. PR #876 had merged first; the two PRs share `security-reference.adoc` and merged without conflict.
- Sonar: lane claim `step.sonar-roundtrip:done`, no `open_issue_count` fact. Measured directly: PR #877 quality gate `OK`; `main` has 0 open issues (`resolved=false`, total 0). Consistent with the lane.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-18 --status shipped`
- [x] row `pr` stamped (`877`)
- [x] row `landing` stamped (`landings/PLAN-18.md`)
- [x] row `plan_marshall_plan_id` stamped (`plan-18-doc-accuracy`)
- [x] `findings-disposition.md`: D-2, D-6, D-9, D-13, D-14, V-17d marked FIXED with the residuals pointing at PLAN-25
- [x] epic.md narrative reconciled (decision entry, Open Defect for the unstamped `effort`, Watch for the two usage estimates and the merge-message residue)
- [x] PLAN-25: the residuals folded in as item 7(j), Expected Surface +entries, claim label added
- [x] upstream report: 2026-11-06 (fifth) addendum written, not sent
- [x] resume anchor updated; `queue-view.md` regenerated and committed

## Follow-Ups

- The four layer-3 majors and the minors: folded into PLAN-25 item 7(j), with one operator question (docs follow the code, or apply `EgressPolicy` to the client back channel).
- The `<<CLIENT-23>>` convention point: PLAN-19's D-11 sweep.
- The D-13 mis-grounding (the cleanup re-grounding checked the contrast file, not the defect site): recorded in the epic.md decision entry. PLAN-19 was re-grounded at `af19b9c2` at this analysis (six verdicts re-stamped, count 69, surface +1 hypothesis), and PLAN-19 and PLAN-25 re-read their own claims at outline.
- Seven candidate-lessons: all plan-marshall-owned process notes, went into the upstream addendum as discarded locally; the project-owned facts they carry (the sibling-occurrence sweep, the conditional https check) are in item 7(j) or already fixed.
