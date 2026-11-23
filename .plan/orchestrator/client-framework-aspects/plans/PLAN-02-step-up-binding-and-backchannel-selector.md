# PLAN-02: CLIENT-34 and CLIENT-35 — step-up subject binding, back-channel session selector, ID-token groups

epic: client-framework-aspects
workstream: WS-02

> Staged plan spec. SELF-SUFFICIENT: the emitted command is a one-line pointer to this file.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the hand-off contract.

## Objective

Close three small engine gaps that session layers above the engine need.

1. **CLIENT-34.** `StepUpResultVerifier` can bind a step-up result to the session's issuer and `sub`,
   compared in constant time, mirroring CLIENT-28's subject binding in `RefreshResultVerifier`.
2. **CLIENT-35.** A back-channel logout handler composes the EXISTING `LogoutTokenValidator` and its
   replay guard (VALIDATION-13.4 and 13.5) and returns a session selector: `sid` alone, or `sub` for
   every session of the user. The HTTP endpoint and the session lookup stay with the caller. The
   addendum's "add a `jti` memory" is NOT in scope: that memory exists.
3. **ID-token groups.** `IdTokenContent` gains a groups accessor that keeps each JSON array element
   whole, the way `AccessTokenContent.getGroups()` already does. Today Audit Notary parses the raw JWS
   payload to get this.

## Deliverables

1. **CLIENT-34.** A subject-bound verification path on `StepUpResultVerifier`: an overload or an
   options record taking the expected issuer and `sub`. A mismatch is refused with a typed refusal. The
   comparison uses `MessageDigest.isEqual` over UTF-8 bytes. The existing unbound call keeps its exact
   behaviour.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety, pm-dev-java:javadoc
   - Persona: implementer
2. **CLIENT-35.** A back-channel logout handling type in `token-sheriff-client`'s `logout` package. It
   validates through the validation module's `LogoutTokenValidator` and maps a valid result to a
   selector:
   - `sid` present → that session;
   - `sid` absent → every session of `iss` and `sub`.

   Refusals pass through unchanged. Decide at outline whether the replay guard's capacity and window must
   become caller-configurable. The guard is package-private and owned by the validator today. Make it
   configurable only if a consumer needs a value other than the validator's. Never share one guard across
   validators silently.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety, pm-dev-java:javadoc
   - Persona: implementer
3. **ID-token groups accessor.** In `token-sheriff-validation`'s `IdTokenContent`. First confirm whether an
   ID token's `groups` claim already reaches `getClaims()` as `STRING_LIST`. If it does, the deliverable
   is the typed accessor plus a test. If not, also add the mapping.
   - Skills: pm-dev-java:java-core, pm-dev-java:javadoc
   - Persona: implementer
4. **Tests** for all three:
   - a match, a mismatched `sub` and a mismatched `iss`, timing-independent by construction;
   - selector by `sid` and by `sub`, a replayed token, an invalid token;
   - groups with a comma inside one element, an empty array, a missing claim.
   - Skills: pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: module-tester
5. **Requirements and specification:** CLIENT-34 and CLIENT-35 in `doc/client/requirements.adoc` (check
   the ids are free); the step-up and back-channel sections of
   `doc/client/specification/step-up-and-logout.adoc`; a cross-reference from VALIDATION-13.4 in
   `doc/validation/requirements.adoc`; and the test-mapping rows.
   - Skills: pm-documents:ref-asciidoc, pm-documents:ref-documentation
   - Persona: documenter

**Done when:**

- Subject-bound step-up refusal, the back-channel selector and the ID-token groups accessor are covered
  by tests that fail at `9c242518`.
- The unbound `StepUpResultVerifier.verify(StepUpChallenge, IdTokenContent)` holds at `9c242518` and
  after, proven by its existing tests staying green unchanged.
- `./mvnw -pl token-sheriff-client -am verify` is green.
- CLIENT-34 and CLIENT-35 are documented with test mappings.
- The pull request's open Sonar read at the head lists zero new-code issues and zero hotspots left to
  review, with the rules left open named. A failed read is recorded as `unknown`, never as `0`.

## Standing Constraints

- Additive. No published signature changes meaning; the fail-closed posture is never loosened.
- Reuse the validation module's logout validator and guard. A second `jti` memory is a defect.
- Build and test with Maven through the project's build skill.
- Sonar: an open finding is fixed in a pushed commit, or DISMISSED ONLY ON THE OPERATOR'S SELECTION,
  recorded as `operator-selected: {issue-key}: {reason}` and transmitted through
  `plan-marshall:workflow-integration-sonar:sonar_rest transition`. A dismissal the run cannot transmit
  is a blocker.

## Claim Labels

- OBSERVED: `StepUpResultVerifier.verify(StepUpChallenge, IdTokenContent)` checks only `acr` and `auth_time`/`max_age` and binds no subject — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/StepUpResultVerifier.java` § `verify` (line ~100, leaf report).
- OBSERVED: `RefreshResultVerifier` binds a nullable subject with `MessageDigest.isEqual` — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/RefreshResultVerifier.java` § `verify` (lines 114-169, leaf report).
- OBSERVED: the logout-token validator and a bounded, fail-closed, digest-only `jti` replay guard already exist in token-sheriff-validation; the guard is a package-private `final class` — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/logout/LogoutTokenReplayGuard.java` § line 56 (orchestrator ls/grep 2026-11-23).
- OBSERVED: the validation result exposes issuer, subject, session id and `jti` — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/logout/LogoutTokenValidationResult.java` § getters (leaf report).
- OBSERVED: `ClaimValue` carries an `asList` field, and `AccessTokenContent.getGroups()` exists — read at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/domain/claim/ClaimValue.java` § line 82.
- HYPOTHESIS: `IdTokenContent` has no groups accessor, and an ID token's `groups` claim may not be mapped to `STRING_LIST` — confirm/refute at `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/domain/token/IdTokenContent.java` § class body and the ID-token claim-mapper configuration (verify-at-outline).
- HYPOTHESIS: no logout-token handling exists in `token-sheriff-client` — confirm/refute at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/logout/` § package listing (leaf grep found only `EndSessionFlow`, `PostLogoutRedirectValidator`) (verify-at-outline).

## Expected Surface

- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/StepUpResultVerifier.java`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/`
- HYPOTHESIS: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/logout/BackChannelLogoutHandler.java` — new type (name is the implementer's) (verify-at-outline)
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/logout/`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/logout/LogoutTokenReplayGuard.java`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/domain/token/IdTokenContent.java`
- OBSERVED: `doc/client/requirements.adoc`
- OBSERVED: `doc/client/specification/step-up-and-logout.adoc`
- OBSERVED: `doc/validation/requirements.adoc`
- OBSERVED: `doc/developer/test-impl-mapping.adoc`

## Dependencies and Sequencing

- Depends on: PLAN-14 (documentation baseline: review, consolidation, renumbering; runs first). Otherwise none. No ADR question touches these three items.
- Overlaps with: PLAN-03 and PLAN-04 on `doc/client/requirements.adoc` and
  `doc/developer/test-impl-mapping.adoc`. PLAN-04 also touches the `logout` package and the
  step-up-and-logout specification. Run in sequence with both.
- Adjacent to: API Sheriff's step-up re-drive subject check (hand-off note in `epic.md`). This plan
  provides the primitive; the gateway fix is API Sheriff's.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/client-framework-aspects/plans/PLAN-02-step-up-binding-and-backchannel-selector.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates and edits
NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message. The orchestrator
owns every other ledger write. The plan reports its outcome through its PR and its inbox message. See
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
