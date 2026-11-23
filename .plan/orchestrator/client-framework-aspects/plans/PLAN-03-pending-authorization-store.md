# PLAN-03: CLIENT-31 — bounded pending-authorization store

epic: client-framework-aspects
workstream: WS-02

> Staged plan spec. SELF-SUFFICIENT: the emitted command is a one-line pointer to this file.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the hand-off contract.

## Objective

Publish in `token-sheriff-client` a bounded store for unfinished logins: requirement CLIENT-31.

- It holds an authorization request's `FlowContext` under a random opaque reference and hands it out
  exactly once.
- It has a total bound and a bound per source key. A source at its bound loses its own oldest entry.
- At the total bound a new start is refused, and the logins already started are kept.
- A start whose authorization request fails (for example a refused PAR) frees its place.

Both BFF consumers built this themselves: API Sheriff `bff/pending/`, 851 lines; Audit Notary
`LoginTransactions`, 159 lines, without a per-source share. The store needs no cookie and no session.
The source key is an input the application derives.

## Deliverables

1. **Store API and in-memory implementation**, in a new package of `token-sheriff-client`.
   - A `put` returns the opaque reference (at least 128 bits from `SecureRandom`, URL-safe). A `take` is
     atomic and single-use.
   - Behaviour on a full store follows API Sheriff's `InMemoryPendingAuthorizationStore`: expired
     entries are purged first; at the per-source bound the source's own oldest entry is evicted; at the
     total bound the start is refused with a typed result or exception, never a silent drop.
   - A `release(reference)` frees the place of a start whose request failed.
   - Thread-safe without a global lock on the take path. Lifetime is enforced by an injectable clock.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety, pm-dev-java:java-lombok, pm-dev-java-cui:cui-logging, pm-dev-java:javadoc
   - Persona: implementer
2. **Limits and defaults.** Defaults as ADR-0032 decides (PLAN-01; the candidates are 5 min, 256 per
   source, 10,000 total). Configurable only within documented floors and ceilings: an out-of-range value
   fails at construction, it is never clamped. Log records through `ClientLogMessages`. A refused start
   logs once per saturation episode, with no per-request WARN flood.
   - Skills: pm-dev-java:java-core, pm-dev-java-cui:cui-logging
   - Persona: implementer
3. **Tests.** Single use under concurrency; per-source eviction order; refusal at the total bound with
   the existing entries kept; release after a failed start; expiry with the injected clock; limit
   validation. Also a native-image guard if the new types need reflection or runtime initialization. The
   `ClientNativeReflectionMetadataTest` / `ClientNativeRuntimeInitializationTest` pattern applies; a
   class holding `SecureRandom` must not be build-time initialized.
   - Skills: pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: module-tester
4. **Requirement and specification.** CLIENT-31 in `doc/client/requirements.adoc` (check the id is
   free; the file is not in numeric order) and its place in `doc/client/specification/retrieval-flow.adoc`.
   Also new rows in `doc/LogMessages.adoc` and `doc/developer/test-impl-mapping.adoc`, and the
   threat-model entry for login flooding in `doc/client/threat-model.adoc`, mirroring API Sheriff's
   BFF-20.
   - Skills: pm-documents:ref-asciidoc, pm-documents:ref-documentation
   - Persona: documenter

**Done when:**

- The store exists, and its single-use, eviction and refusal behaviour, new at `9c242518`, is covered by
  tests that fail at `9c242518` (the type does not exist there).
- `./mvnw -pl token-sheriff-client -am verify` is green, including the JaCoCo gate.
- CLIENT-31 is in `doc/client/requirements.adoc` with a test mapping.
- The pull request's open Sonar read at the head lists zero new-code issues and zero hotspots left to
  review, with the rules left open named. A failed read is recorded as `unknown`, never as `0`.

## Standing Constraints

- Additive and opt-in. No existing public signature changes, and the fail-closed posture is never
  loosened.
- No cookie, HTTP or session type in this module. The source key is an opaque `String` input.
- Build and test with Maven through the project's build skill. Never run `javac` directly.
- Where the project analyses its pull requests with Sonar, an open Sonar finding is fixed in a pushed
  commit, or DISMISSED ONLY ON THE OPERATOR'S SELECTION. The question tool names each finding key. The
  progress record records the selection as `operator-selected: {issue-key}: {reason}`. The chosen key is
  transmitted through `plan-marshall:workflow-integration-sonar:sonar_rest transition --issue-key {key}
  --transition {falsepositive|wontfix}`. A dismissal the run cannot transmit is a blocker.

## Claim Labels

- OBSERVED: no pending-authorization store exists in the engine; a case-insensitive search for `PendingAuth|pendingLogin|FlowContextStore|FlowContextRegistry` over the repository returned no file — read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/flow/FlowContext.java` § `FlowContext` (a single-use record, ADR-0022), leaf search 2026-11-23.
- OBSERVED: API Sheriff's bounds are `MAX_PENDING_PER_SOURCE = 256` and `FIXED_TTL = Duration.ofMinutes(5)` — read at `API-Sheriff/api-sheriff/src/main/java/de/cuioss/sheriff/gateway/bff/pending/InMemoryPendingAuthorizationStore.java` § line 85 and `PendingAuthorizationRecord.java` § line 97 (orchestrator grep).
- OBSERVED: Audit Notary bounds 10,000 total, 10 min, no per-source share, and frees the slot on PAR failure — read at `Audit-Notary/audit-notary/src/main/java/de/cuioss/notary/audit/security/LoginTransactions.java` § `MAX_PENDING` and `LoginService.java` § lines 134-150 (leaf report).
- HYPOTHESIS: API Sheriff needs no release because it pushes PAR before storing — confirm/refute at `API-Sheriff/api-sheriff/src/main/java/de/cuioss/sheriff/gateway/bff/login/LoginFlow.java` § lines 157-167 (verify-at-outline); the `release` operation stays either way, because Audit Notary stores first.
- HYPOTHESIS: `FlowContext` is runtime-initialized in native image and the new store type needs the same treatment — confirm/refute at `token-sheriff-client/src/main/resources/META-INF/native-image/de.cuioss.sheriff.token/token-sheriff-client/native-image.properties` § `--initialize-at-run-time` (verify-at-outline).
- Verify-first clause: the defaults are ADR-0032's (PLAN-01). If PLAN-01 has not landed, stop and do not invent them.

## Expected Surface

- HYPOTHESIS: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/pending/` — new package (verify-at-outline; the name is the implementer's to choose)
- HYPOTHESIS: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/pending/` — new tests (verify-at-outline)
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/internal/ClientLogMessages.java`
- OBSERVED: `token-sheriff-client/src/main/resources/META-INF/native-image/de.cuioss.sheriff.token/token-sheriff-client/native-image.properties`
- OBSERVED: `doc/client/requirements.adoc`
- OBSERVED: `doc/client/specification/retrieval-flow.adoc`
- OBSERVED: `doc/client/threat-model.adoc`
- OBSERVED: `doc/LogMessages.adoc`
- OBSERVED: `doc/developer/test-impl-mapping.adoc`

## Dependencies and Sequencing

- Depends on: PLAN-14 (documentation baseline: review, consolidation, renumbering; runs first), PLAN-01 (defaults).
- Overlaps with: PLAN-02 and PLAN-04 on `doc/client/requirements.adoc`, `ClientLogMessages.java`,
  `doc/LogMessages.adoc` and `doc/developer/test-impl-mapping.adoc`. Run it in sequence with them.
- Adjacent to: `flow/AuthorizationCodeFlow.java` and `flow/ParClient.java` (the store holds their
  `FlowContext` but changes neither); PLAN-08 consumes the store at the BFF edge.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/client-framework-aspects/plans/PLAN-03-pending-authorization-store.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates and edits
NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message. The orchestrator
owns every other ledger write. The plan reports its outcome through its PR and its inbox message. See
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
