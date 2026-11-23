# Adversarial review — where do Parts B and C live? (2026-11-23)

**Question:** do `token-sheriff-session` (Part B) and `token-sheriff-bff-quarkus` (Part C) go into
Token Sheriff as new modules, or into a new repository? The engine seams CLIENT-31..35 stay in Token
Sheriff either way.

**Method:** two read-only `execution-context-level-3` reviewers, each arguing ONE side and each
required to attack its own case. The orchestrator re-checked the load-bearing claims; those claims are
marked ✔ below.

## The two verdicts

| Side | Verdict | Confidence |
|---|---|---|
| Separate repository | Split out (`cuioss/session-sheriff`), keep the seams in Token Sheriff | 55 / 100 |
| Same repository | New modules in this reactor, behind ADR-0032 | 78 / 100 |

## Arguments that survived the self-attack

**For a separate repository:**
1. **Lockstep release and supply-chain blast radius.** This is the strongest argument either side
   made. One version for every module (✔ `pom.xml`; `.github/project.yml` has a single
   `current-version`) means a cookie or CSRF fix in the BFF layer forces a release of
   `token-sheriff-validation`. That release reaches validation-only consumers such as nifi-extensions.
   The same-repo reviewer conceded this partially defeats their side.
2. **Consumers already move at different speeds:** nifi-extensions 0.9.8, API Sheriff 0.9.9,
   Audit Notary 0.9.10 (✔). The session layer is new design and will churn more than the engine.
3. **Project identity.** `doc/client/architecture.adoc:22` says "We do not build a BFF here"; CLIENT-19
   and the threat model say the same. Staying requires an ADR to qualify these statements.
4. **Build cost.** The modules add a Keycloak login-flow and native IT layer to 15–20-minute PR runs, a
   second merge-queue run and a 3-JDK matrix.

**For the same repository:**
1. **ADR-0001 already decided this, with triggers for splitting out** (✔
   `doc/adr/0001-Validation_and_Client_Module_Split_with_a_Commons_Base_Layer.adoc`). It says to keep
   every capability in one multi-module repository and to promote a module to its own repository only
   when one of these bites:
   - an independent release cadence is genuinely blocked;
   - a module brings a heavier or higher-risk dependency tree;
   - audience, governance or licence diverges;
   - a distinct maintainer set needs a separate review process.

   It adds that splitting out later is cheap, while merging back after a premature split is expensive.
   It solved the same identity problem ("validate, never acquire") through artifact naming. No trigger
   has bitten yet: the only new dependency is Quarkus, which `token-sheriff-quarkus-parent` already
   carries, and the maintainers are the same.
2. **The layers will change together.** CLIENT-31..35 are designed together with SESSION-2/3/7.
   SESSION-1 is re-modelling onto engine types, not extraction. The previous epic's history (16 PRs in
   10 days, 9 of them residual fixes to seams shipped days earlier) shows how much a fresh seam keeps
   moving after release. Across two repositories every such fix is a round trip: PR → release → bump PR
   → consumer PR.
3. **`TokenStore` was designed for exactly this consumer** (✔ its Javadoc: "a BFF that needs a
   persistent, shared store … plugs its own implementation").
4. **A separate repository would develop against Token Sheriff snapshots, and snapshot deploys are
   unreliable** (✔ `.github/project.yml` comment: snapshot deploys were cancelled at the 30-minute cap
   from 2026-09-04, and none was published after build 47).
5. **Shared infrastructure:** the 3-IDP integration stack (Keycloak, Dex, Zitadel), Sonar, the merge
   queue, the release skill, one BOM with the load-bearing Quarkus order already solved, and one
   threat model and LogMessages registry. This weighs less than it looks: the workflows are thin callers
   of `cuioss-organization`, so setting up a new repository takes hours, not days (conceded).

## Arguments that did not hold

- **"Session code raises the attack surface for validation users."** Refuted. Users only pull the
  artifacts they depend on. The separate-repo reviewer withdrew it.
- **"The code would have to be back-ported from Java 25 to 21."** Weaker than the reviewer claimed.
  Token Sheriff compiles for Java 21 and both consumers for 25 (✔). Twelve `bff` files use the unnamed
  variable `_`, which Java 21 does not allow (✔), and only about 3 of them are in the extractable core.
  The back-port is renaming `_` to a named unused variable, and consumers on 25 can use a Java 21
  library unchanged.
- **"Setup cost is decisive"**, in either direction. Both sides conceded it is not.

## Where the two sides meet

Each side's own conditions for changing its mind point at the same answer:

- The separate-repo reviewer would keep the code here for the build phase and split at 1.0, if the
  seams cannot be released ahead of the session work or keep changing weekly. That is the case: the
  ADR, the seams and SESSION-1 are all being designed now.
- The same-repo reviewer would split once it is measurable that the session layer has its own cadence:
  3 or more releases fixing only session/BFF code, under 20 % of session PRs touching engine paths, a
  dependency that must stay out of validation's supply chain (for example a Redis client), PR CI above
  about 20 minutes, or a distinct maintainer or security process.

## Orchestrator recommendation

**Same repository now. ADR-0032 records split-out triggers for these modules in ADR-0001's style,
with measurable thresholds, and the decision is re-checked at 1.0 or when a trigger fires.**

Mitigations to make binding in PLAN-05 and PLAN-08 if this is chosen:
- **ArchUnit:**
  - session is framework-agnostic;
  - session uses only published `client` and `validation` packages, never `..internal..` or
    package-private seams;
  - client and validation never depend on session or bff.
- **Opt-in artifacts and a clear BOM statement.** Lockstep versioning is chosen explicitly and
  documented.
- **Separate threat models** in `doc/session/` and `doc/bff/`, cross-linked to the client one.
- **CI scoping:** the new modules stay out of the e2e `-pl` list, and only the BFF login IT joins the
  integration-test module.
- **PR size:** each PR stays at 100 files or fewer (the CodeRabbit ceiling).

This recommendation answers the strongest separate-repo argument (lockstep release) only partly. A
session fix still produces a validation release. That cost is accepted until the triggers measure it
as real.
