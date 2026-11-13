# PLAN-03: Client key material and config validators

epic: api-sheriff-seams
workstream: WS-01

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-03-client-key-material-and-config-validators.md` and is queued as one row file, `queue/PLAN-03.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT: the emitted command is a one-line pointer and carries no
> brief, so every per-plan carry is authored here and nowhere else.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## Objective

Publish four small pieces that API Sheriff currently re-implements because TokenSheriff keeps them private or
lacks them: a public mapping from a `PublicKey` to its JWK members (today a private helper inside the DPoP
proof generator), a bounded PEM key-pair loader, the back-channel-origin and egress-host-entry validators as
static refusals a caller can reuse for its own configuration errors, and a revoke-if-supported call on the
revocation client. The egress allow-list builders also stop silently accepting entries that can never match
(a blank entry, a `host:port` entry). Key and validator publication is additive; the allow-list tightening is
a deliberate fail-closed change that the spec states and bounds explicitly.

## Deliverables

1. **Public `PublicKey` to JWK-members utility.** A public, documented utility returns the RFC 7517/7518/8037
   JWK members of an RSA, EC P-256 or Ed25519 public key (`kty` and the type's members, with the EC
   coordinates left-padded to the fixed width), and `DpopProofGenerator` uses it instead of its private
   `buildJwk`. Verification: (a) for RSA, EC P-256 and Ed25519 test keys the members equal the members
   `DpopProofGenerator` produced before the change (a golden comparison captured from the unchanged
   generator), so every existing `jkt` thumbprint is identical; (b) a coordinate with leading zero bytes is
   padded to 32 bytes (the existing generator behaviour, asserted at the utility); (c) an RSA modulus or
   exponent has no leading zero byte; (d) an EC key on a curve other than P-256, an Ed25519-curve mismatch
   and an unsupported key type are refused with `IllegalArgumentException` and a fixed message that carries
   no key material; (e) the returned map cannot be modified; (f) `DpopProofGeneratorTest` passes without
   edits to existing assertions.
2. **Bounded PEM key-pair loader.** A public loader reads a key file with a hard byte bound and returns the
   key pair, so a gateway need not parse PEM itself. It handles the two-block layout API Sheriff uses (one
   unencrypted PKCS#8 `PRIVATE KEY` block and one X.509 `PUBLIC KEY` block, in a single file), refuses
   everything else, and verifies that the two halves belong to one key. Verification: (a) a file over the
   bound is refused before it is fully read (a test supplies bound-plus-one bytes); (b) a directory, a
   missing file and a non-regular file are refused; (c) text outside a PEM block, an unknown or repeated
   label, an encrypted `ENCRYPTED PRIVATE KEY` block, a PKCS#1 `RSA PRIVATE KEY` and a SEC1 `EC PRIVATE KEY`
   are each refused with a message that names the defect and not the file content or path; (d) a private
   and public half of two different keys are refused (a sign-and-verify or equivalent probe); (e) an
   RSA key below the configured minimum modulus and an EC key off P-256 are refused; (f) a valid RSA and a
   valid EC pair load and yield a `KeyPair` that `DpopProofGenerator` and `PrivateKeyJwtAuth` accept. The
   key-type policy (allowed types, RSA minimum, whether Ed25519 is admitted) is caller-visible
   configuration, not a hard-coded API Sheriff rule: API Sheriff refuses EdDSA and TokenSheriff's DPoP
   generator supports it, so the loader must let the caller decide.
3. **Back-channel-origin validator, published.** The origin-shape check that `ClientConfiguration` runs
   privately is exposed as a static refusal that returns an `Optional<String>` of fixed text (empty when the
   value is a bare `http`/`https` origin), and `ClientConfiguration` is rebuilt on top of it. Verification:
   one criterion per accepted-or-refused shape — null (absent), blank, malformed URI, relative URI,
   non-http(s) scheme, no host, user-info, query, fragment, non-empty path, a single trailing `/` (accepted
   and stripped by the configuration, accepted by the validator) — and for each refusal, the reason text
   does not contain the input (a credential in user-info must never be echoed); the existing
   `ClientConfigurationTest` and `BackChannelOriginMappingTest` cases pass unchanged.
4. **Egress-host entry validator, published, and fail-closed builders.** One static refusal in the
   validation module's transport package decides whether an allow-list entry is a bare host: blank is
   refused, `name:digits` and `[v6]:digits` are refused (they never match, since matching is host-exact),
   a bare IPv6 literal is admitted. `ClientConfiguration.validateAllowedEgressHosts`, `EgressPolicy`'s
   builder, `WellKnownConfig`'s builder and `HttpJwksLoaderConfig`'s builder use it and refuse a bad entry at
   build time instead of ignoring it (the two loader builders) or storing it (the policy builder).
   Verification: (a) every refusal class above has a test per call site, each throwing an
   `IllegalArgumentException` whose text does not echo the entry; (b) a valid bare host (`keycloak`,
   `localhost`, a bare IPv6 literal) is accepted and matches as before; (c) the case-insensitive matching of
   `EgressPolicy.check` is unchanged; (d) the existing configured values in the repository
   (`keycloak`, `localhost` in tests and in the integration-tests `application.properties`) still pass;
   (e) a `null` entry is refused, not ignored; (f) the Quarkus path (`IssuerConfigResolver` handing a CSV of
   hosts to `allowedEgressHost`) fails startup with the typed configuration failure when a CSV element is
   blank or carries a port, and does not when every element is a bare host. This deliverable changes default
   behaviour for those inputs only (see Standing Constraints and Operator decisions).
5. **`RevocationClient` revoke-if-supported.** A new method takes the resolved `ProviderMetadata`, revokes
   at its `revocation_endpoint` when one is advertised and returns without a request when it is not,
   reporting which happened. Verification: with an endpoint, exactly one request goes to that endpoint with
   the token and hint; without one, no request is sent and the return value says so; the method delegates to
   the existing `revoke(...)` so a subclass that overrides `revoke` still observes the call; a failure of the
   endpoint propagates as `revoke` does today.
6. **Documentation.** The key utility, the loader, the validators and the tightening are documented.
   Verification: one new requirement or an amendment per area, with the id the next free `CLIENT-N` (or
   `COMMONS-N` for the egress allow-list) at write time; `doc/client/requirements.adoc` CLIENT-24 and
   `doc/commons/requirements.adoc` / `doc/commons/threat-model.adoc` (egress allow-list text) and
   `token-sheriff-validation/doc/configuration/openid-discovery.adoc` (the `allowedEgressHost` note) match the
   code; `doc/client/specification/test-strategy.adoc` names the new tests.

Done when: all criteria pass in both modules, the full `-Ppre-commit` plus `clean install` sequence of
`AGENTS.md` is green, and the Sonar open-issue read at the PR head is `0` or every remaining issue is
dismissed with a recorded reason (`unknown` for a failed read, never `0`).

**Operator decisions needed at outline**

- **Allow-list tightening: breaking fail-closed, or opt-in.** Rejecting blank and `host:port` entries makes
  a configuration that starts today fail to start. It is the fail-closed direction and the pre-1.0 rules
  permit it, but it is a default-behaviour change for those inputs. The operator confirms the break (the
  spec's proposal) or asks for the validators to be published with the builders left as they are.
- **Where the key utility and the loader live, and whether they are public API.** `token-sheriff-client`
  (`dpop`/`auth`) or `token-sheriff-validation` (next to `JwkThumbprintUtil`). The finding wants them public
  because API Sheriff consumes them; the operator confirms they are supported API from 0.9.10.
- **Key-type policy of the loader** (allowed types, RSA minimum, Ed25519): caller-supplied policy object, or
  a fixed TokenSheriff policy with API Sheriff's EdDSA refusal staying on the API Sheriff side.
- **Whether the loader also replaces `DecryptionKeyLoader.loadFromPem`** (the unbounded `Files.readString`
  read of a JWE decryption key). It is the same defect class on a different file; fixing it widens the
  surface into `token-sheriff-validation-quarkus`.
- **Return shape of the revoke-if-supported call** (boolean, an enum, or `Optional`).

## Standing Constraints

- Pre-1.0 rules of `AGENTS.md` apply: no `@Deprecated`, no transitional comments.
- **Default behaviour.** Deliverables 1, 2, 3 and 5 are additive and change no existing code path's
  output; this is proven by the unchanged existing tests (`DpopProofGeneratorTest`, `ClientConfigurationTest`,
  `RevocationClientTest`) plus the golden comparison of deliverable 1. Deliverable 4 is the one stated
  exception: its change is limited to entries that today are blank, null or carry a port, and is proven
  bounded by criterion (b) and (d) of that deliverable.
- **Overridability (AGENTS.md "Preferred overloads", ADR-0006).** `RevocationClient` is a non-final public
  class and `RecordingRevocationClient` (in `TokenLifecycleManagerApplyRefreshTest`) overrides `revoke`;
  the new method must call `revoke(...)` and `TokenLifecycleManager` keeps calling `revoke(...)` as it does
  today, so the existing double keeps observing every revocation. The same two questions are answered in
  the PR for any other overload added.
- **Secrets and input handling (security).** Key material, key-file content, file paths and configured
  origins are never echoed in an exception message, log line or `toString`; every refusal text is fixed.
  The file read is bounded before parsing. Logging follows `AGENTS.md` (`CuiLogger`, `%s` only,
  `LogRecord` for INFO/WARN/ERROR with the next free identifier in range at write time, documented in
  `doc/LogMessages.adoc`; none is assumed here). Adversarial tests are required for deliverables 2, 3 and 4
  (oversized file, PEM injection and label confusion, mismatched halves, weak and off-curve keys,
  credential-bearing origins, control characters and Unicode in host entries).
- Tests: JUnit 5, AAA, no Mockito/PowerMock/Hamcrest; `@GeneratorsSource` or the repository's key
  fixtures for key data; parameterized tests for three or more similar variants; attack-database style for
  the injection inputs; new or grown test classes at or under 500 lines.
- No new dependency without operator approval (the key and PEM code uses the JDK only, as the consumer's
  copy does). Never gate on `test-compile`/`test`; use `verify`.
- Sonar applies (`.github/project.yml` declares `sonar.project-key`, `AGENTS.md` names the SonarCloud
  new-code gate the coverage authority): an open Sonar issue is fixed in a pushed commit, or dismissed with a
  stated reason recorded as `{issue-key}: {reason}` in the run's progress record and transmitted through
  `plan-marshall:workflow-integration-sonar:sonar_rest transition --issue-key {key} --transition
  {falsepositive|wontfix}`. A dismissal the run cannot transmit is a blocker, never a silent pass.

## Claim Labels

- OBSERVED: `DpopProofGenerator.buildJwk(PublicKey, JwsAlgorithm)` is `private static` and builds the JWK
  members for RSA (`kty`, `e`, `n`), EC P-256 (`kty`, `crv`, `x`, `y`, coordinates fixed-width) and Ed25519
  (`kty`, `crv`, `x`); it also requires the key type to match the algorithm (`requireKeyTypeMatches`). The
  findings cite ":234": the method declaration is on that line — read at
  `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/dpop/DpopProofGenerator.java` §
  `buildJwk`
- OBSERVED: the consumer's `ClientSigningKey.publicMembersOf` renders only RSA (`n`, `e`) and EC P-256
  (`crv`, `x`, `y`) members, without `kty`, and refuses EdDSA elsewhere; `base64UrlFixedWidth` left-pads the
  coordinate to 32 bytes — read at
  `api-sheriff/src/main/java/de/cuioss/sheriff/gateway/bff/client/ClientSigningKey.java` §
  `publicMembersOf`, `base64UrlFixedWidth` (:486-530). The consumer's file is 605 lines, as the findings
  say
- OBSERVED: the consumer's loader is bounded (`MAX_KEY_FILE_BYTES = 16 * 1024`), accepts exactly one
  `PRIVATE KEY` and one `PUBLIC KEY` block, rejects encrypted, PKCS#1 and SEC1 labels with a conversion hint,
  requires RSA of at least 2048 bits or EC P-256, refuses EdDSA, and probes that the halves belong together
  (`PROBE_MESSAGE`); the byte read is `KeyFiles.readBounded` (92 lines), which refuses a missing file, a
  non-regular file, an unreadable file and an over-bound file, never echoing the path — read at
  `api-sheriff/src/main/java/de/cuioss/sheriff/gateway/bff/client/ClientSigningKey.java` § `load`,
  `requireSupportedKey`, constants (:100-131) and
  `api-sheriff/src/main/java/de/cuioss/sheriff/gateway/bff/KeyFiles.java` § `readBounded`
- OBSERVED: `DecryptionKeyLoader.loadFromPem(Path)` reads with an unbounded `Files.readString` (:70) and
  accepts only a PKCS#8 `BEGIN PRIVATE KEY` block. It lives in the Quarkus validation module, not in
  `token-sheriff-validation` (the findings' path "validation-quarkus/.../jwe" resolves to
  `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/jwe/DecryptionKeyLoader.java`),
  so a framework-agnostic client cannot depend on it — read at that file § `loadFromPem`, `parsePemContent`
- OBSERVED: `TokenSheriffClientProducer.clientAuthentication` throws `IllegalStateException` for
  `PRIVATE_KEY_JWT` and `TLS_CLIENT_AUTH` ("not yet plumbed into the produced client bean graph") — read at
  `token-sheriff-quarkus-parent/token-sheriff-client-quarkus/src/main/java/de/cuioss/sheriff/token/client/quarkus/TokenSheriffClientProducer.java`
  § `clientAuthentication` (:236-262). This plan does not change it (adjacent, see Dependencies)
- OBSERVED: `JwkThumbprintUtil` exists at
  `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/util/JwkThumbprintUtil.java`
  (class at :42) and `DpopProofGenerator` feeds it the single JWK map that also fills the proof header —
  read at `DpopProofGenerator.java` § constructor (:217-221); `PrivateKeyJwtAuth` exists in
  `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/auth/`
- OBSERVED: `ClientConfiguration.validateBackChannelOrigin` is `private static`, throws
  `IllegalArgumentException`, and its messages deliberately do not echo the input (blank, URI syntax with
  reason and index only, scheme/host, user-info, query, fragment, path; a single trailing `/` is stripped) —
  read at `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/config/ClientConfiguration.java`
  § `validateBackChannelOrigin` (:493-532 as the findings say)
- OBSERVED: `ClientConfiguration.validateAllowedEgressHosts` (:428) refuses only null and blank entries —
  it accepts `host:port` — read at the same file § `validateAllowedEgressHosts`
- OBSERVED: the consumer duplicates both: `OidcClientRules.backChannelOriginShapeRefusal` (:491, fixed-text
  `Optional<String>` refusals) and `RuleSupport.egressHostRefusal` with `HOST_WITH_PORT` (:65, :163; refuses
  blank and `name:digits` / `[v6]:digits`, admits a bare IPv6 literal), the latter also called from
  `TokenValidationRules` (:109) — read at
  `api-sheriff/src/main/java/de/cuioss/sheriff/gateway/config/validation/rule/OidcClientRules.java` and
  `.../rule/RuleSupport.java` and `.../rule/TokenValidationRules.java`. The findings' "~:490-520" and
  "~:163-172" match
- OBSERVED: the allow-list builders are lenient in two different ways. `EgressPolicy.EgressPolicyBuilder.allowedHost`
  (:235) stores `host.toLowerCase(Locale.ROOT)` with no check (a blank or `host:port` entry is stored; a
  `null` throws `NullPointerException`); `WellKnownConfig.WellKnownConfigBuilder.allowedEgressHost` (:225)
  and `HttpJwksLoaderConfig.HttpJwksLoaderConfigBuilder.allowedEgressHost` (:516) silently ignore a `null` or
  blank entry (documented "ignored when null or blank") but store a `host:port` entry. The findings say
  "silently accept blank / host:port"; the accurate statement is "store blank (policy builder) or drop blank
  (loader builders), and store host:port in all three" — read at
  `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/EgressPolicy.java`,
  `.../WellKnownConfig.java` and `.../HttpJwksLoaderConfig.java` § the three builder methods
- OBSERVED: `EgressPolicy.check` compares `uri.getHost()` lower-cased against the stored set (:135-139), so
  an entry carrying a port can never match — read at
  `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/EgressPolicy.java` §
  `check`
- OBSERVED: the Quarkus validation path splits a CSV and passes each element to `allowedEgressHost`
  (`IssuerConfigResolver` :639-643), so a blank CSV element reaches the lenient builder — read at
  `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/config/IssuerConfigResolver.java`
  § the `ALLOWED_EGRESS_HOSTS` read. Whether `ConfigValueParser.splitCsv` already drops blank elements was
  not read
- OBSERVED: `RevocationClient.revoke(String revocationEndpoint, String token, @Nullable String tokenTypeHint,
  ClientAuthentication)` takes a bare endpoint; `TokenLifecycleManager` checks
  `metadata.getRevocationEndpoint().ifPresent(...)` at two sites (:947, :1172); the consumer re-checks the
  same in `BffRuntimeProducer.revokeRefreshToken` (:1295, the findings' "~:1293") — read at
  `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/lifecycle/RevocationClient.java` §
  `revoke`, `.../lifecycle/TokenLifecycleManager.java` § the two `getRevocationEndpoint` sites, and
  `api-sheriff/src/main/java/de/cuioss/sheriff/gateway/quarkus/BffRuntimeProducer.java` § `revokeRefreshToken`
- OBSERVED: `RevocationClient` is `public class` and a test double `RecordingRevocationClient` extends it
  and overrides `revoke` — read at
  `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/lifecycle/TokenLifecycleManagerApplyRefreshTest.java`:306-316
- HYPOTHESIS: `URI.getHost()` returns an IPv6 literal in square brackets, so a bare IPv6 allow-list entry
  (admitted by the consumer's refusal rule) may never match `EgressPolicy.check` — confirm/refute at
  `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/EgressPolicy.java` §
  `check` and `EgressPolicyTest` (verify-at-outline). If true, the published validator must either normalise
  bracketed literals or the spec must state that bare IPv6 entries are admitted by shape and matched by the
  bracketed form
- HYPOTHESIS: `DpopProofGenerator` does not check that the private and public halves of its `KeyPair` belong
  together — confirm/refute at the constructor `DpopProofGenerator(KeyPair, String, Supplier<String>)`
  (:189-221) (verify-at-outline); if true, the loader's pair probe is the only such check on this path
- HYPOTHESIS: `PrivateKeyJwtAuth` enforces no minimum RSA modulus, so the loader's configurable minimum is
  the only place that rule lives for TokenSheriff consumers — confirm/refute at
  `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/auth/PrivateKeyJwtAuth.java` §
  constructor (verify-at-outline)
- Verify-first clause: before scoping, settle (1) the operator decisions above, (2) the module and package of
  each new public type, checked against `ClientArchitectureTest` and the validation module's public-API
  documentation (`package-info.java` states some packages are internal; do not place a public seam in a
  package documented as internal), (3) the bracketed-IPv6 behaviour above, (4) the caller set of each of the
  four builder methods (search main, test and the Quarkus modules, because tightening a builder breaks every
  caller that passes a bad value), and (5) every line number above against HEAD. A refutation loops back to
  re-scope.

## Expected Surface

- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/dpop/DpopProofGenerator.java`:234 — `buildJwk`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/dpop/DpopProofGenerator.java`:189 — `DpopProofGenerator(KeyPair, String, Supplier<String>)`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/auth/PrivateKeyJwtAuth.java`:1 — `PrivateKeyJwtAuth` (read; consumes the loaded `PrivateKey`)
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/util/JwkThumbprintUtil.java`:42 — `JwkThumbprintUtil` (read; candidate home of the new utility)
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/config/ClientConfiguration.java`:428 — `validateAllowedEgressHosts`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/config/ClientConfiguration.java`:493 — `validateBackChannelOrigin`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/lifecycle/RevocationClient.java`:84 — `revoke`
- OBSERVED: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/lifecycle/TokenLifecycleManager.java`:947 — `revokeOrphanedSuccessor` (read; the revocation call sites)
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/EgressPolicy.java`:235 — `allowedHost`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/WellKnownConfig.java`:225 — `allowedEgressHost`
- OBSERVED: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/HttpJwksLoaderConfig.java`:516 — `allowedEgressHost`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/config/IssuerConfigResolver.java`:639 — `ALLOWED_EGRESS_HOSTS` read
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/main/java/de/cuioss/sheriff/token/quarkus/jwe/DecryptionKeyLoader.java`:68 — `loadFromPem` (changed only if the operator widens the plan to it)
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-client-quarkus/src/main/java/de/cuioss/sheriff/token/client/quarkus/TokenSheriffClientProducer.java`:236 — `clientAuthentication` (read only; `PRIVATE_KEY_JWT` plumbing is out of scope)
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/dpop/DpopProofGeneratorTest.java`:1 — `DpopProofGeneratorTest`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/config/ClientConfigurationTest.java`:1 — `ClientConfigurationTest`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/internal/BackChannelOriginMappingTest.java`:1 — `BackChannelOriginMappingTest`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/lifecycle/RevocationClientTest.java`:1 — `RevocationClientTest`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/lifecycle/TokenLifecycleManagerApplyRefreshTest.java`:306 — `RecordingRevocationClient`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/auth/PrivateKeyJwtAuthTest.java`:1 — `PrivateKeyJwtAuthTest`
- OBSERVED: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/boundary/ClientArchitectureTest.java`:1 — `ClientArchitectureTest`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/commons/transport/EgressPolicyTest.java`:1 — `EgressPolicyTest`
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/commons/transport/WellKnownConfigTest.java`:104 — `allowedEgressHost("localhost")` call site
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/commons/transport/HttpJwksLoaderConfigTest.java`:147 — `allowedEgressHost("localhost")` call site
- OBSERVED: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/validation/util/JwkThumbprintUtilTest.java`:1 — `JwkThumbprintUtilTest`
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-validation-quarkus/src/test/java/de/cuioss/sheriff/token/quarkus/jwe/DecryptionKeyLoaderTest.java`:1 — `DecryptionKeyLoaderTest` (touched only with the optional widening)
- OBSERVED: `token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/src/main/resources/application.properties`:37 — `allowed-egress-hosts=keycloak` (existing valid value; must keep working)
- HYPOTHESIS: `token-sheriff-client/src/main/java/de/cuioss/sheriff/token/client/dpop/` or `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/validation/util/` — new public key-to-JWK utility and the bounded key-pair loader (new files; final package decided at outline) (verify-at-outline)
- HYPOTHESIS: `token-sheriff-validation/src/main/java/de/cuioss/sheriff/token/commons/transport/` — new egress-host entry validator, or a static method on `EgressPolicy` (verify-at-outline)
- HYPOTHESIS: `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/dpop/` — new test classes for the key-to-JWK utility and the PEM loader, including an attack-database-style loader test (to be created) (verify-at-outline)
- HYPOTHESIS: `token-sheriff-validation/src/test/java/de/cuioss/sheriff/token/commons/transport/` — new test class for the egress-host entry validator and the three builder call sites (to be created) (verify-at-outline)
- OBSERVED: `doc/client/requirements.adoc`:313 — `CLIENT-24`
- OBSERVED: `doc/client/README.adoc`:52 — the Document map states the requirement range (`CLIENT-1`–`CLIENT-26` after PLAN-01 landed) and :79 names the covered requirements in the Test strategy row; both are updated in the same deliverable that adds a new `CLIENT-N` (folded from inbox message client-native-image-registration-001: a new requirement id makes every restated range stale)
- OBSERVED: `doc/commons/requirements.adoc`:105 — the egress relaxation paragraph (`allowedEgressHosts`)
- OBSERVED: `doc/commons/threat-model.adoc`:108 — the `allowed-egress-hosts` bypass text
- OBSERVED: `token-sheriff-validation/doc/configuration/openid-discovery.adoc`:96 — the `allowedEgressHost` example and note
- OBSERVED: `doc/client/specification/test-strategy.adoc`:1 — `Client Specification — Test Strategy`
- OBSERVED: `doc/LogMessages.adoc`:1 — only if a new LogRecord is added

## Dependencies and Sequencing

- Target release: 0.9.10 (the repository is at `0.9.10-SNAPSHOT`).
- `parallelization_scope` is 1: plans of this epic run sequentially, one at a time, in ordinal order. This
  plan runs after PLAN-02.
- Depends on: none by code (PLAN-01 and PLAN-02 are order only).
- Overlaps with: PLAN-02 and PLAN-04, all three in `token-sheriff-client`. Shared files: PLAN-02 and this plan
  both edit `ClientConfiguration` (PLAN-02 possibly adds a field; this plan changes two private validators);
  PLAN-04 and this plan both touch `TokenLifecycleManager`'s neighbourhood only if the revoke-if-supported
  method is adopted there (this plan does not require it). This plan alone, among PLAN-01 to PLAN-04, touches `token-sheriff-validation`
  (`EgressPolicy`, `WellKnownConfig`, `HttpJwksLoaderConfig`). Later validation-side plans of this epic (the
  source findings list a validation-seams plan and a JWKS-loader plan; their ordinals are the epic ledger's
  to assign) may touch the same JWKS loader configuration, so whichever runs later rebases on this plan's
  builder change. Every plan edits
  `doc/client/requirements.adoc` and `doc/client/specification/test-strategy.adoc`; requirement and log ids
  are next free at write time, never pre-assigned.
- Adjacent to: `TokenSheriffClientProducer` (the `PRIVATE_KEY_JWT` "not yet plumbed" throw is left as is;
  producing `private_key_jwt` in the CDI graph is a separate capability), the consumer-side concerns API
  Sheriff keeps (the key `Purpose`, the two-key separation rule, the EdDSA refusal, its JWKS publication
  endpoint), and `DecryptionKeyLoader` (touched only on operator decision).
- API Sheriff's matching deletion (the PEM parsing and JWK-member code in `ClientSigningKey`, `KeyFiles`, the
  two config-validator duplicates and the revocation-endpoint re-check) is its own plan, AS PLAN-34, which
  waits for a released 0.9.10. It is NOT part of this spec.

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/api-sheriff-seams/plans/PLAN-03-client-key-material-and-config-validators.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
