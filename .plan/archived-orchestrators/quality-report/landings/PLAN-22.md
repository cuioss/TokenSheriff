# Landing Analysis: PLAN-22 — Residual documentation, test and ADR items left open by PLAN-01 to PLAN-06

epic: quality-report
workstream: WS-05
pr: 863 (https://github.com/cuioss/TokenSheriff/pull/863)

> Landing record for one shipped plan. Written by the `analyze` verb (inbox scan, message
> `plan-22-residual-docs-tests-adrs-001.md`) after verifying the claims against the merged source, the PR state read
> through the CI abstraction, and SonarCloud. A landing message is a lead, not a fact.

## Deliverable Fidelity vs Spec

Corroborated against `origin/main` at the merge commit `38aefc0a` (read: `git show --stat`, the diffs of
`TokenLifecycleManager`, `TokenValidator`, `ErrorTextSanitizer`, `JweDecryptor` and `JwkKey`, a grep of the tree for the
dropped items) and the PR (`ci pr view --pr-number 863`: `state: merged`, merge commit
`38aefc0adf8e06c02a160e17d10967c32f14e219`). 46 files, +2908 / -1007.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. Wording and Javadoc | shipped; two items dropped as already correct | Edited: `token-decryption.adoc`, the validation-quarkus README, ADR-0005 and ADR-0010, `AGENTS.md` (+60/-, the two PLAN-20 advisories), `step-up-and-logout.adoc` and `StepUpHandler` (negative `max_age`), `TokenBuilder` comments, the three `UncheckedThrowBoundaryTest` EXEMPT comments, `DpopConfig` Javadoc (read: now 360 s, matching the constant). `TokenSheriffClientProcessor` Javadoc reads `CLIENT-20` for the problem+json mapper and `CLIENT-21` for the producer (read). `applyRefresh` orphaned-successor wording is present in `TokenLifecycleManager` (read). Dropped as already correct: the `AccessTokenCache` iat-less Javadoc (read at line 249 to 252, states the rule) and the benchmark rotation counter (below). |
| 2. Test gaps | shipped (counts only) | `AccessTokenCacheTest` +44, `JweDecryptorTest` +71, `TokenValidatorTest` +109, `TokenValidatorStartupInitializerTest` +19, and new `NonValidatingJwtParserJweDecodeModeTest` (245 lines). Test bodies not read line by line. |
| 3. Test hygiene | shipped, one item partly | Raw U+2028/U+2029 characters: none left in any `*.java` file (grep, read). `MockTokenRepository`: already uses `Math.floorMod` (read, lines 176 to 183), so nothing to fix. `ConfigValueParserTest` +14 reaches the empty-candidate guard. `TokenValidator.close` is the one behaviour fix (below). The 400-line split: only `NonValidatingJwtParserJweTest` was split (1018 to 369, plus two new classes of 245 and 256 lines). `JweDecryptorTest` (1020 lines) and `AccessTokenCacheTest` (1000) are still over 400 and grew; the "record why not" text the spec asked for is not in the landing message. See Follow-Ups. |
| 4. ADRs | shipped | New ADR 0020 to 0026 (seven files, 100 to 124 lines each); ADR-0017 amended (+44); ADR-0005 and ADR-0010 rewritten for the conditional clear. The provisional-number check: no file under `doc/adr/` contains the word "provisional" (grep, read), so 0016 to 0018 carry no provisional marker on `main`. |
| 5. Sanitiser duplication | shipped | `ErrorTextSanitizer.sanitize(value, replacement, maxLength, truncationMarker)` added; `JweDecryptor` and `JwkKey` now call `sanitize(value, '?', limit, "...")`. Equivalence read: the removed loop tested `Character.isISOControl(c) \|\| U+2028 \|\| U+2029`; the shared one tests `c < 0x20 \|\| 0x7F..0x9F` plus the same two separators, which is the same set. Output unchanged; `ErrorTextSanitizerTest` +80, `JwkKeyHandlerTest` touched. |

**Behaviour changes.** Two, both fail-closed:

1. `TokenValidator.close()` (in the spec): every release step now runs after any `Throwable`, the last failure is
   thrown and earlier ones are attached as suppressed (`releaseInOrder`, read).
2. `TokenLifecycleManager.refresh` (NOT in the spec, which said "Javadoc only" for that file): the catch around
   `verifiedRefreshedIdToken` widened from `IllegalStateException` to `IllegalStateException | TokenValidationException`,
   so a refreshed ID token that fails pipeline validation now quarantines the session when the authorization server
   rotated (`quarantineRejectedRotation`), where before only an inconsistency did. A new
   `RefreshIdTokenValidationQuarantineTest` (+178) covers it, and `ClientLogMessages` / `RefreshFailureClassification`
   Javadoc were corrected to say so (the latter now says a switch statement over `kind()` needs a failing default arm).
   The PR body says "two small, tested behaviour changes" and names only the first.

**Surface delta (landing-check, `unmeasured`).** The message carries no declared or realized set, so the check could not
compare. Computed here: the spec declared about 20 paths; 46 changed. Added beyond the declaration: the seven ADRs,
`TokenLifecycleManager`, `ClientLogMessages`, `RefreshFailureClassification`, `TokenValidator` (declared as a
hypothesis), `NonValidatingJwtParser*Test`, `JweTestTokenFactory`, `RefreshIdTokenValidationQuarantineTest`, `LogMessages.adoc`,
`doc/client/specification/token-handling.adoc`, `test-strategy.adoc`, `doc/client/threat-model.adoc`. Declared but not
touched: `MockTokenRepository` (nothing to fix) and `AccessTokenCache` Javadoc (already correct). The spec is a record.

## Metrics and Anomalies

- Tokens: 50,315,372; duration 64,587 wall seconds, about 17.9 hours, the longest plan of the epic (landing message).
  The population is mixed (inline plus sub-agent), and phases 3 and 5 were re-entered, so treat it as an upper bound.
- Effort: `unknown`. `landing-check` reports `complete: false`, missing key `effort`; `refresh-runtime-stamp` returned
  `refreshed: false, reason: model_unknown`. Second plan in a row with this gap (PLAN-14 was the first): recorded as an
  Open Defect and added to the upstream addendum.
- Plugin version on the landing is 0.1.2046; this session runs 0.1.2047.
- Anomalies: functional review and code review were both closed as `operator_override` after loop-back rounds, each
  accepting one residual finding. The message does not name the two findings, and the plan workspace is gone
  (`.plan/local/plans` holds only `NO_PLAN`), so they cannot be read back. The pre-push gate reused a whole-tree verify
  with no build of its own; test-compile and module tests were UNRUN at that step. `archive-plan` and `emit-landing`
  were `pending` in the step list (expected for the emitting step).

## Routing and Merge Behavior

- Review: CodeRabbit ran (the check is `SUCCESS`, 404 s); functional and code review done under the overrides above.
- CI/merge: `ci checks status` on #863 returns `overall_status: success` across 24 checks. Merged through the merge
  queue as `38aefc0a` (`merge_mechanism=merge_queue`), read back as `state: merged`.
- Sonar: gate `OK` on PR 863 read directly (new coverage 96.3, duplication 0.0, hotspots reviewed 100, all ratings 1).
  `main` at revision `38aefc0a`: 1 open issue, the known `java:S8786` at `JwtTokenTamperingUtil.java:450`, owned by
  PLAN-15 (unchanged).
- Concurrency: ran beside PLAN-13 and PLAN-14 and with PLAN-15 and PLAN-16 started after; no collision reported.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-22 --status shipped`
- [x] row `pr` stamped — value 863
- [x] row `landing` stamped — value `landings/PLAN-22.md`
- [x] row `plan_marshall_plan_id` stamped `plan-22-residual-docs-tests-adrs` (the message sender)
- [x] `epic.md`: Decisions entry, Queue annotation, Open Defect (`effort`), Watches
- [x] PLAN-17, PLAN-19 and PLAN-25 specs annotated with what PLAN-22 changed in their declared paths
- [x] upstream addendum extended (effort gap again; the unnamed overrides)
- [x] message archived; resume anchor and `queue-view.md` updated

## Follow-Ups

- PLAN-17 declares `TokenSheriffClientProcessor.java` and `TokenValidator.java`; PLAN-22 changed both (the second by
  +92 lines of `close`/`releaseInOrder`). PLAN-17 re-reads both at outline.
- PLAN-19 indexes the ADRs: its spec says the tree holds ADR-0001 to 0015 and the hub lists one; the tree now holds
  0001 to 0026 and ADR-0017, ADR-0005 and ADR-0010 were amended. PLAN-19 allocates and indexes by rule, so it re-reads
  `doc/adr/` and `doc/README.adoc` at outline. `AGENTS.md` (also declared there) changed by +60/-.
- PLAN-25 deliverable 6 (log-record sanitisation sweep) should call the new
  `ErrorTextSanitizer.sanitize(value, replacement, maxLength, marker)` overload rather than a fourth inline copy, and
  its guard test is modelled on the shape `ErrorTextSanitizerTest` now has. Added as a note to the spec.
- Test-size budget: `JweDecryptorTest` and `AccessTokenCacheTest` stay over 400 lines and nothing records why. I
  could not find the 400-line figure in `AGENTS.md` or any `*.adoc`/`*.md` under version control (grep; it appears only
  in the PLAN-01 landing and the PLAN-22 spec, so it comes from the plan-marshall standards), and 52 test classes in the tree
  exceed 400 lines (17 exceed 600, three exceed 1000, for example `DpopProofValidatorTest` at 1375; count corrected
  2026-11-06, this record first said about a dozen), so the budget is not enforced anywhere I can read. Recorded as a Watch for
  the PLAN-25 split decision, not folded into a plan: it needs an operator ruling on whether the budget is real.
- The `TokenLifecycleManager` quarantine widening is a behaviour change that landed under a docs plan. Not a defect
  (tested, fail-closed), but it is worth a line in the client threat-model review if one is ever staged.
- Candidate lessons: none (the message carries no lesson); the preference emitter reported no owed hints.
