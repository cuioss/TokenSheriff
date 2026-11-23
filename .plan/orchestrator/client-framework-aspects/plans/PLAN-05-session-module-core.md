# PLAN-05: `token-sheriff-session` — module, record, store, deadlines (SESSION-1 to SESSION-3)

epic: client-framework-aspects
workstream: WS-03

> Staged plan spec. SELF-SUFFICIENT: the emitted command is a one-line pointer to this file.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the hand-off contract.
> ⛔ This spec was staged BEFORE ADR-0032 (PLAN-01) was accepted, so re-ground it against the merged
> ADR at outline. Wherever the ADR decides differently, the ADR wins and the plan re-scopes.

## Objective

Create the new pure-Java module `token-sheriff-session`, with the core of a server-side session
lifecycle and no HTTP and no Quarkus:

- **SESSION-1, the session record and a bounded store.** The cookie holds a re-issuable random handle,
  never the internal identity. Sessions are indexed by `sid` and by `sub`, bounded in total and per
  subject; on per-subject overflow the subject's oldest session ends.
- **SESSION-2, deadlines.** A session has an absolute lifetime and an idle time. Only an explicit
  `recordAccess` moves the idle deadline. An expired session is evicted lazily, plus by a sweep the
  caller schedules.
- **SESSION-3, create versus update.** `bind` creates a session and is refused at the bound. `persist`
  only replaces an existing session and reports one that is gone, so a logout always wins over an
  in-flight refresh or step-up.

Extract rather than invent. API Sheriff's `bff/session/` (`SessionBinding`, `SessionStore`,
`InMemorySessionStore`, `ServerSessionBinding`, `SessionRecord`, `SessionCookieCodec`; about 1,600 lines)
already implements this and is Quarkus-free. Drop its gateway-only fields (`activeScopes`,
`grantedScopes`). Token custody follows ADR-0032, question 5.

## Deliverables

1. **Module scaffold.**
   - `token-sheriff-session/pom.xml`, with parent `token-sheriff-parent`, modelled on
     `token-sheriff-client/pom.xml` including its coverage settings.
   - A `<module>` entry in the root `pom.xml` after `token-sheriff-client`, and a `dependencyManagement`
     entry in `bom/pom.xml`.
   - The package root, and `META-INF/native-image` metadata if any type needs it (the CLIENT-26 pattern).
   - Skills: pm-dev-java:java-core, pm-dev-java:manage-maven-profiles
   - Persona: implementer
2. **SESSION-1 record and store.**
   - An immutable record holding `iss`, `sub`, `sid`, `acr`, `amr`, `auth_time`, the groups as a list,
     and the tokens as ADR-0032 decides.
   - A handle that can be re-issued while the session keeps its identity. Handles are at least 128 bits
     from `SecureRandom`, compared in constant time.
   - The store SPI and an in-memory implementation with the `sid` and `sub` indexes, the total bound and
     the per-subject bound.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety, pm-dev-java:java-lombok, pm-dev-java-cui:cui-logging, pm-dev-java:javadoc
   - Persona: implementer
3. **SESSION-2 deadlines and SESSION-3 bind/persist semantics.**
   - An injectable clock. Resolving a session renews nothing; only `recordAccess` does. Lazy eviction,
     plus a `sweep()` the caller schedules.
   - `bind` is refused at the bound. `persist` reports a gone session and never creates one. Decide
     compare-and-set versus presence-only for `persist` (epic Watch) and record the decision in the module
     docs.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety
   - Persona: implementer
4. **Tests.** Port the behavioural pins of API Sheriff's `InMemorySessionStoreTest` and
   `ServerSessionBindingTest`; re-author them, never copy their gateway fixtures. Cover:
   - the bounds and per-subject eviction order;
   - a re-issued handle keeping its identity;
   - idle versus absolute expiry with the injected clock;
   - `persist` on a gone session;
   - concurrent `bind` at the bound.
   - Skills: pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: module-tester
5. **Documentation set** in `doc/session/` (or wherever ADR-0032 places it):
   - `requirements.adoc` with SESSION-1 to SESSION-3 (prefix per ADR-0032);
   - `architecture.adoc`, and the threat-model entries ADR-0032 moves from API Sheriff (BFF-21: session
     id, lifetime);
   - a link from `doc/README.adoc`, and rows in `doc/developer/test-impl-mapping.adoc`.
   - Skills: pm-documents:ref-asciidoc, pm-documents:ref-documentation
   - Persona: documenter

**Done when:**

- `./mvnw verify` is green across the reactor, including the new module's JaCoCo gate.
- The new module's tests cover SESSION-1 to SESSION-3. All of them are new behaviour that fails at
  `9c242518`, since the module is absent there.
- `token-sheriff-bom` exports the artifact.
- The documentation set exists and is linked.
- The pull request's open Sonar read at the head lists zero new-code issues and zero hotspots left to
  review, with the rules left open named. A failed read is recorded as `unknown`, never as `0`.

## Standing Constraints

- No dependency on Quarkus, Vert.x, CDI or Jakarta Servlet: the module is framework-neutral. Its
  allowed dependencies are `token-sheriff-client`, `token-sheriff-validation`, `cui-java-tools`, Lombok
  and JSpecify.
- ⛔ **Repository-placement mitigations (operator decision, 2026-11-23: same repository with split-out
  triggers; ADR-0032 question 12).** Deliverable 4 includes a `SessionArchitectureTest` modelled on
  `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/boundary/ClientArchitectureTest.java`.
  It enforces:
  - the module is framework-agnostic;
  - it uses only published `client` and `validation` packages, never `..internal..`;
  - its slices are free of cycles.

  Two more mitigations:
  - **Client and validation never depend on session.** This holds through the Maven dependency
    direction. Add an ArchUnit guard as well if the client's architecture test can express it.
  - **Shared versioning, opt-in artifact.** The module stays on the reactor's one version, by
    deliberate choice. The BOM docs state that `token-sheriff-session` is opt-in.
- Each PR stays at 100 files or fewer. If the extraction needs more, split it.
- **Java 21 baseline** (`maven.compiler.release` 21, unchanged). API Sheriff compiles for 25, so code
  extracted from its `bff` package that uses the unnamed variable `_` (or any other post-21 feature)
  is rewritten for 21. The baseline stays 21 while nifi-extensions runs inside NiFi on JDK 21 (epic
  Watch). This applies equally to PLAN-06 and PLAN-07.
- Log records use the module's own identifier range as ADR-0032 assigns it, never `ApiSheriff-*` ids.
- Do not edit API Sheriff or Audit Notary: this plan only adds the module.
- Build and test with Maven through the project's build skill.
- Sonar: an open finding is fixed in a pushed commit, or DISMISSED ONLY ON THE OPERATOR'S SELECTION,
  recorded as `operator-selected: {issue-key}: {reason}` and transmitted through
  `plan-marshall:workflow-integration-sonar:sonar_rest transition`. A dismissal the run cannot transmit
  is a blocker.

## Claim Labels

- OBSERVED: API Sheriff's `bff` package is Quarkus-free except `jakarta.inject.Singleton` in `bff/runtime/GatewayJson.java` — read at `API-Sheriff/api-sheriff/src/main/java/de/cuioss/sheriff/gateway/bff/` § imports (orchestrator grep 2026-11-23).
- OBSERVED: API Sheriff's defaults are TTL 3600 s, idle 1800 s, total 10,000, per subject 10 — read at `API-Sheriff/api-sheriff/src/main/java/de/cuioss/sheriff/gateway/config/model/OidcConfig.java` § lines 347-369 (leaf report).
- OBSERVED: API Sheriff's `SessionRecord` holds raw tokens and gateway scope fields, not `TokenLifecycleManager` — read at `API-Sheriff/api-sheriff/src/main/java/de/cuioss/sheriff/gateway/bff/session/SessionRecord.java` § fields (leaf report; 0 `TokenLifecycleManager` matches by orchestrator grep).
- OBSERVED: Audit Notary puts the internal session id into the cookie and renews it on refused requests — read at `Audit-Notary/audit-notary/src/main/java/de/cuioss/notary/audit/web/RequestGate.java` § `api` / `session()` (leaf report).
- OBSERVED: a new top-level module needs a root `<module>`, a BOM entry and a parent of `token-sheriff-parent` — read at `pom.xml` § `<modules>` and `bom/pom.xml` § `dependencyManagement` (leaf report).
- HYPOTHESIS: "tokens through `TokenLifecycleManager`" is feasible without a second store, because `TokenStore` is an SPI keyed by session id — confirm/refute at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/lifecycle/TokenStore.java` § interface methods (verify-at-outline; moot if ADR-0032 chose a session-owned record).
- Verify-first clause: re-ground the whole spec against the merged ADR-0032 before scoping.

## Expected Surface

- HYPOTHESIS: `token-sheriff-session/` — new module; unresolvable by the reader until it exists (verify-at-outline)
- OBSERVED: `pom.xml`
- OBSERVED: `bom/pom.xml`
- HYPOTHESIS: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/boundary/ClientArchitectureTest.java` — only if the "client never depends on session" guard is expressible there (verify-at-outline)
- HYPOTHESIS: `doc/session/` — new documentation set, placement per ADR-0032 (verify-at-outline)
- OBSERVED: `doc/README.adoc`
- OBSERVED: `doc/developer/test-impl-mapping.adoc`

## Dependencies and Sequencing

- Depends on: PLAN-14 (documentation baseline: review, consolidation, renumbering; runs first), PLAN-01 (ADR-0032).
- Overlaps with: PLAN-06 and PLAN-07, inside the new module, which the gate cannot see; run strictly
  before them. Also PLAN-08 on `bom/pom.xml`.
- Adjacent to: `token-sheriff-client/.../lifecycle/` (used, not changed); API Sheriff `bff/session/`
  (the extraction source, read-only).

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/client-framework-aspects/plans/PLAN-05-session-module-core.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates and edits
NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message. The orchestrator
owns every other ledger write. The plan reports its outcome through its PR and its inbox message. See
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
