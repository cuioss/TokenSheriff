# Landing Analysis: PLAN-15 — Validation test gaps: tamper outcomes, boundaries, confusion, dup kid

epic: quality-report
workstream: WS-01
pr: 867 (https://github.com/cuioss/TokenSheriff/pull/867)

> Landing record for one shipped plan. Written by the `analyze` verb (inbox scan, messages
> `plan-15-validation-test-gaps-001` to `-009`) after verifying the claims against the merged source, the PR state read
> through the CI abstraction, and SonarCloud. A landing message is a lead, not a fact.

## Deliverable Fidelity vs Spec

Corroborated against `origin/main` at the merge commit `262369d6` (read: `git show --stat`, the production diffs of all
nine main-source files, a `git grep` of every remaining DSL-JSON call site) and the PR (`ci pr view`-equivalent through
`gh`: `state: MERGED`, merge commit `262369d68c2f5a94d75d3e3cfe9a4e7776c2e0b6`). 23 files, +704 / -92.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. TV-6 tampered tokens keep 3 segments, assert the precise event; Sonar `S8786` | shipped | `JwtTokenTamperingUtil` +183 (header members located by JSON member name, per message 005), `JwtTokenTamperingUtilTest` +81, `TokenValidationSecurityTest` rewritten (+55/-). Sonar: `main` has 0 open issues at revision `262369d6` (the `S8786` at `JwtTokenTamperingUtil` is gone; read through the public API). |
| 2. gaps 2/5 exact `exp` / `nbf` boundaries | shipped | `ExpirationValidatorTest` +48 (counts only). |
| 3. gap 3 RS256 to HS256 confusion at unit level | shipped | `TokenSignatureValidatorTest` +36 (counts only). |
| 4. gap 6 duplicate-kid winner and counter | shipped | `JWKSKeyLoaderTest` +52; message 005 says the reverse order RS384-before-RS256 was added after a CodeRabbit finding so the test fails on an order-blind loader. |
| 5. TV-4 / TV-7 | PARTLY: TV-7 shipped, TV-4 NOT done | `TestTokenHolder` +31/-: the Javadoc note on fixed alg/kid. The three enum tests (`LoaderStatusTest`, `MeasurementTypeTest`, `EventCategoryTest`) are not in the diff: the functional review flagged each removal as a blocking `weakened_test` finding and the operator chose to restore them (commit `7b8ed2a7`), so the files equal `main`. `deliverables_done=5` of 6 reflects exactly this. |
| 6. DSL-JSON truncated-escape sweep, validation module | shipped, widened | `IOException | ArrayIndexOutOfBoundsException` now in `JwksHttpContentConverter`, `WellKnownConfigurationConverter`, `JwksParser` (both reads), `NonValidatingJwtParser` (two catches) (read). Tests: converters +14 and +26, `JwksParserTest` +22, `NonValidatingJwtParserTest` +19. |

**Behaviour changes beyond the spec, read in the diff.** All are fail-closed and each has a test:
`DpopProofValidator` (catch widened, and the decode-failure text now passes through `ErrorTextSanitizer`),
`DiscoveryResolver` (catch widened; the logged message is sanitised and the raw exception object is no longer handed to
`LOGGER.error`), `ParClient` and `UserInfoClient` (catch widened). The operator chose "fix all four now" at the
code-review gate for the extra sites (messages 003 and 004). `JwksParser` also sanitises the `JWKS_INVALID_JSON` message.

**Surface delta (landing-check, `unmeasured`).** The message carries no declared or realized set. Computed here: declared
14, realized 23. Added beyond the declaration: the three client sources and their three tests, `DpopProofValidator`,
`DpopProofValidatorTest`, `JwksParser`. The spec is a record.

**The landing's "unreviewed sites" claim is mostly refuted.** The message says `RevocationClient`, `JWKSKeyLoader` and
`JweDecryptor` still catch only `IOException` around DSL-JSON. A `git grep` of main sources for `dslJson.deserialize`,
`newReader` and `JsonReader` finds none in those three files. The remaining reads are `MapRepresentation.fromJson`
(callers `DpopProofValidator` and `NonValidatingJwtParser` both catch the exception) and the already-guarded sites above.
No unguarded site is left; a class-level guard test is still missing and is now PLAN-25 deliverable 2.

## Metrics and Anomalies

- Tokens: 21,079,068; duration 56,495 wall seconds, about 15.7 hours (landing message; the population is mixed, read it as
  a floor).
- Effort: `unknown`. `landing-check` `complete: false`, missing key `effort`. Eleventh producer: recorded as an Open Defect.
- Anomalies: functional review and code review were both closed as `operator_override` (two post-push criteria could
  not be observed pre-push: D1.C3 Sonar issue gone, D6.C2 PR body line); both are met after the push (Sonar read above).
  The pre-push gate recorded a whole-tree `verify -Ppre-commit` (3852 tests) with per-bundle and bare test-compile arms
  degraded, because client tests need the validation test-jar. A transition 5-execute to 6-finalize was refused once
  for two commits with no change-ledger row (message 006). CodeRabbit quota waits were not reported as a lesson.

## Routing and Merge Behavior

- Review: functional and code review under the overrides above; CodeRabbit raised two findings after the in-run review
  (an order-blind loader test, a first-match `kid` locator), both fixed in `94fb462b`; Sourcery approved.
- CI/merge: `ci checks status` on #867 returns `overall_status: success`, 24 checks. Merged as `262369d6`, `MERGED` at
  2026-11-06T08:00:10Z.
- Sonar: gate `OK` on PR 867 read directly (new coverage 100.0, duplication 0.0, hotspots reviewed 100, all ratings 1).
  `main`: 0 open issues (it was 1 after PLAN-23, 44 before).
- Concurrency: ran beside PLAN-16 and PLAN-22; no collision reported.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-15 --status shipped`
- [x] row `pr` stamped — value 867
- [x] row `landing` stamped — value `landings/PLAN-15.md`
- [x] row `plan_marshall_plan_id` stamped `plan-15-validation-test-gaps` (the message sender)
- [x] `findings-disposition.md`: TV-6, TV-7, gaps VAL 2, 3, 5, 6, 7 FIXED; TV-4 NOT FIXED by operator decision
- [x] `epic.md`: Decisions entry, Queue annotation, Open Defect (`effort`), Watches
- [x] eight candidate-lesson messages dispositioned (below); all nine messages archived
- [x] PLAN-25 re-scoped: deliverable 2 replaced by the class-level guard test, deliverable 6 loses `JwksParser`, and the
  JWE-header catch gap added to 4(d)

## Follow-Ups

- **TV-4**: the operator kept the trivial enum tests. Recorded as a deliberate project convention, no plan.
- **Lesson 001** (a deliverable that mandates test removal collides with `weakened_test`): plan-marshall-owned (outline and
  functional-review contract); upstream addendum, not sent. The project side is settled: trivial enum tests stay.
- **Lesson 002** (criteria observable only after push): plan-marshall-owned; upstream addendum.
- **Lesson 003** (a defect-class survey scoped to named sites missed siblings): split. The rule is plan-marshall-owned
  (outline) and went into the addendum; the project fact (DSL-JSON surfaces a truncated escape as
  `ArrayIndexOutOfBoundsException`, so every parse of external input must catch it) is FOLDED into PLAN-25 deliverable 2
  as the guard test.
- **Lesson 004** (a CWE-117 comment claimed sanitisation while the raw exception was logged): project-owned, FOLDED into
  PLAN-25 deliverable 6 (check comments against logger arguments; the guard covers exception arguments).
- **Lesson 005** (non-discriminating test fixtures): plan-marshall-owned (a review-checklist item for test code); addendum.
- **Lesson 006** (finalize-time commits carry no change-ledger row): plan-marshall-owned; addendum. It echoes the PLAN-13
  lesson on the same ledger gap, so this is the second report of one cause.
- **Lesson 007** (`effort resolve-target` rejects `--dispatch-context`): plan-marshall-owned doc/script drift; addendum.
- **Lesson 008** (client tests need the validation test-jar): project knowledge, an owed architecture hint for
  `token-sheriff-client` of kind `tip`. Run through `architecture enrich` on the operator's standing instruction
  (see `epic.md`).
- PLAN-17 and PLAN-25 claims anchored at `306e6053` were re-grounded against `262369d6`; PLAN-25's claims 5 and 10 were
  contradicted by this landing and re-scoped.
