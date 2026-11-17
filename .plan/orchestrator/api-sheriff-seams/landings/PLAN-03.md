# Landing Analysis: PLAN-03 — Client key material and config validators

epic: api-sheriff-seams
workstream: WS-01
pr: #915 (https://github.com/cuioss/TokenSheriff/pull/915), merged as dfab40cbd0a926403722698dc10aaa84b042c04d

> Landing record for one shipped plan. Lives at `landings/PLAN-03.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact.

Source: inbox message `plan-03-client-key-material-config-validators-006.md` (lane `plan-marshall`, plan id `plan-03-client-key-material-config-validators`),
plus five candidate-lesson messages (`-001` to `-005`). PR state read through `ci pr view` (`state: merged`, merge commit as above, base `main`).
`inbox landing-check`: `complete: false` (missing `unresolved_review_threads`, `review_body_findings_total`, `review_body_findings_handled` and
`sonar_open_new_issues`, all `unknown` in the landing); `surface_delta` is `unmeasured` (neither the declared nor the realized surface was supplied), so the
declared-vs-realized comparison below was made by hand from `git show --stat dfab40cb` (39 files, +3238/-283). The lane's account of its own finalize (steps,
the functional-review waiver, the post-merge JMH timeout, the Sourcery refusal) is its own and was not re-run. The archived plan directory
(`2026-11-17-plan-03-client-key-material-config-validators`) was read for its work log and decision log only.

## Deliverable Fidelity vs Spec

The spec lists six deliverables; the landing reports `deliverables_total=9`, `deliverables_done=9`. The leaf found no file in the diff that no deliverable
explains, so the 9 against 6 is a task split (the spec's deliverables 1 to 6 plus the golden test, the Quarkus mapping and the documentation as separate tasks), not extra scope.
The five operator decisions at outline were taken by the lane (the spec asked for them) and were read off the merged code, not from a record of the operator's answer:
**allow-list tightening** — fail closed, in every builder; **where the types live** — `token-sheriff-client` (`dpop`, `auth`, `config`) for the key utility, the loader and the
origin validator, the validation module's `commons.transport` for the egress validator, all public; **key-type policy** — a caller-supplied `KeyPairPolicy`; **`DecryptionKeyLoader`** — not
widened (neither it nor the Quarkus `jwe` package is in the diff); **revoke-if-supported return** — `boolean`.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. Public key-to-JWK-members utility | shipped-as-specified, with one addition | `JwkPublicMembers.of` (leaf: RSA `e`/`n` unsigned, EC coordinates left-padded to 32 bytes, Ed25519 `x`, `Map.of` unmodifiable); `DpopProofGenerator` now calls it (`:188-190`, re-read). The addition is an on-curve and in-field check for EC P-256 that the old private method did not have, and fixed refusal texts replacing the old texts that named the curve and key algorithm; the key-type-versus-algorithm check now runs after member construction, so the refusal order changes for a key that fails both (leaf). Tests: `JwkPublicMembersTest`, `DpopProofGeneratorJktGoldenTest` (iterates `SUPPORTED_ALGORITHMS`, fails on a key type with no fixture, pins a padded-x EC key; leaf). That the golden values were captured before the move is claimed in the Javadoc and cannot be checked by reading |
| 2. Bounded PEM key-pair loader | shipped-as-specified | `PemKeyPairLoader.readBounded` reads through `readNBytes(MAX_FILE_BYTES + 1)` and refuses on length (`:179-191`, re-read); label state machine, pair probe (sign and verify with the type's own algorithm) and RSA floor on the public modulus are the leaf's reading (`:254-291`, `:333-360`, not re-read). Tests: `PemKeyPairLoaderTest` (318 lines, derived from `KeyType.values()`), `PemKeyPairLoaderAdversarialTest` (257). The D2(f) claim that `PrivateKeyJwtAuth` "accepts" the pair means it accepts the private half only (see L1-4) |
| 3. Back-channel-origin validator, published | shipped-as-specified | `BackChannelOriginValidator.refusal` returns an `Optional<String>` of constants; `ClientConfiguration.validateBackChannelOrigin` is rebuilt on it and still strips one trailing `/`. The leaf found the same check order and refusals as the old private method; the one change is the unparsable-URL text, which no longer carries the parser reason and index (deliberate, asserted by the test) |
| 4. Egress-host validator, published, builders fail closed | shipped-modified: broader than specified, and criterion (f) as written cannot be met | `EgressHostValidator.refusal` and `requireBareHost` (`:101-144`, re-read); `EgressPolicy`, `WellKnownConfig`, `HttpJwksLoaderConfig` store the canonical form, `ClientConfiguration.validateAllowedEgressHosts` validates and keeps the configured spelling (`:452-464`, re-read; L2-2). The Quarkus path: `IssuerConfigResolver.configureAllowedEgressHosts` (`:550-569`, re-read) drops blank elements through `ConfigValueParser.splitCsv` (`:47-52`, re-read) and turns every builder refusal into a startup `IllegalStateException`. L1-1 and L1-2 below |
| 5. `RevocationClient` revoke-if-supported | shipped-as-specified | `revokeIfSupported` (`:151-167`, re-read): arguments checked first, no endpoint returns `false` with no request, an endpoint calls the overridable public `revoke(...)` and returns `true`, a failure propagates. The test double override is exercised (leaf: `RevocationClientTest`) |
| 6. Documentation | shipped-as-specified in the code-facing places, with a test-strategy gap (L2-4) | `CLIENT-30` is the next free id (README range `CLIENT-29` to `CLIENT-30`, leaf); `COMMONS-3` amended with no new `COMMONS-N`; threat model, `openid-discovery.adoc` and `transport.adoc` updated. The `CLIENT-30` row of `test-strategy.adoc` (`:603-621`, re-read) names the key, PEM, origin, `ClientConfigurationTest` and `RevocationClientTest` cases and none of the egress validator, builder or Quarkus tests |

Diff files outside the declared Expected Surface (all explained by deliverables 4 and 6, per the leaf): `ClientConfigMapper.java` (Javadoc only, 11 comment lines, re-read), `ClientRuntimeConfig.java`, `JwtPropertyKeys.java`,
both `quarkus-config-doc.adoc`, `TokenSheriffClientProducerTest.java` (`:392`), `IssuerConfigResolverEgressHostTest`, `BackChannelEgressPolicyTest`, `UncheckedThrowBoundaryTest.java` (one new `CONSTRUCTION_TIME` exemption for `EgressHostValidator#requireBareHost`,
re-read: an entry for the new construction site, not a widening of an existing one) and `doc/commons/specification/transport.adoc`. The new classes were declared as HYPOTHESIS. Declared but not touched: `JwkThumbprintUtil`,
`PrivateKeyJwtAuth`, `TokenLifecycleManager`, `DecryptionKeyLoader`, `TokenSheriffClientProducer`, the integration-tests `application.properties`, which fits the decisions taken. `doc/LogMessages.adoc` is not touched and the leaf found no new `LogRecord`.

## Three-Layer Analysis

The landing was read on three layers by one read-only leaf (`execution-context-level-3`, `orchestrator.analyze` surface), and the orchestrator re-read the
points marked "re-read" at `dfab40cb` (`git show`) before writing them here. Points marked "leaf" are the leaf's reading that this analysis did not re-read:
they are recorded as the leaf's account, not as settled. The reading is **by reading, no build, no test run, no write**.

- **Dispatch:** one read-only leaf — reason: every full-ship landing gets its own leaf (merged-code depth, no shared context with another landing's read).
- **Verdict:** layer 1 findings (2 minor, 3 info, all spec premises settled by the code); layer 2 findings (5 minor, 3 info); layer 3 findings (1 minor not shown by reading, 1 minor test-assumption not shown by reading, the rest info). No blocker and no major defect was found at any layer.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| L1-1 | The spec's criterion D4(f) ("`IssuerConfigResolver` fails startup when a CSV element is blank") contradicts the premise the spec left open ("whether `ConfigValueParser.splitCsv` already drops blank elements was not read"): it does. `splitCsv` trims every segment and drops empty ones, so a blank element never reaches the builder and the criterion cannot be met on the issuer path. The lane resolved it by documenting "blank dropped" | checked | confirms (the premise held, the criterion was wrong) | `ConfigValueParser.java:40-41`, `:47-52`; `IssuerConfigResolver.java:543` (re-read) |
| L1-2 | The constraint "the change is limited to entries that today are blank, null or carry a port" is exceeded: the validator also refuses whitespace, control and non-ASCII characters, URL syntax (`/ @ ? # %`), invalid labels, `idp.123`, over-long names and invalid IPv6. Each could never match a `URI.getHost()` value, so the Objective is served, but the stated bound does not hold. On the client path `ClientConfigMapper:114-116` reads `getOptionalValues` and passes each element on without trimming, so a value such as `keycloak, dex` now fails the mapping where ` dex` was stored and never matched (the mapper Javadoc says entries are not trimmed; whether SmallRye trims is not shown) | checked | refutes | `EgressHostValidator.java:92`, `:101-123`, `:146-163` (re-read); `ClientConfigMapper.java:114-116` (re-read) |
| L1-3 | HYPOTHESIS H1 holds: `EgressPolicy.check` compares `uri.getHost()` lower-cased, and `URI.getHost()` reports an IPv6 literal in brackets, so a bare `::1` entry could never match; the fix stores `[::1]` (`requireBareHost`) | checked | confirms | `EgressPolicy.java:148` (leaf); `EgressHostValidator.java:133-144` (re-read) |
| L1-4 | HYPOTHESES H2 and H3 hold: `DpopProofGenerator` derives `jwk` and `jkt` from `keyPair.getPublic()` only and never touches `getPrivate()`, so a hand-built `KeyPair` with mismatched halves is still accepted; `PrivateKeyJwtAuth` takes a bare `PrivateKey` and checks no RSA minimum. The loader's pair probe and `KeyPairPolicy` floor are therefore the only such checks, and only for loaded pairs | checked | confirms | `DpopProofGenerator.java:188-191` (re-read); `PrivateKeyJwtAuth.java:88-96` (re-read) |

**Layer 1 verdict:** findings — 4 (L1-1 and L1-2 minor, L1-3 and L1-4 info)

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| 1 | `JwkPublicMembers`, `DpopProofGenerator:188-190` | `JwkPublicMembersTest`, the golden test (leaf) | none | checked (call site re-read, tests leaf) |
| 2 | `PemKeyPairLoader:179-198` | `PemKeyPairLoaderTest`, `...AdversarialTest` (leaf) | none | checked (bound re-read, the rest leaf) |
| 3 | `BackChannelOriginValidator`, `ClientConfiguration` | `BackChannelOriginValidatorTest`, `ClientConfigurationTest`, `BackChannelOriginMappingTest` (leaf) | none | checked (leaf) |
| 4 | `EgressHostValidator`, three builders, `ClientConfiguration:452-464`, `IssuerConfigResolver:550-569` | `EgressHostValidatorTest`, per-site builder tests, `IssuerConfigResolverEgressHostTest`, `TokenSheriffClientProducerTest:392` (leaf) | the narrative claims L2-1 to L2-3 | checked, with deviations |
| 5 | `RevocationClient:151-167` | `RevocationClientTest` `revokeIfSupported*` (leaf) | none | checked |
| 6 | the doc files | a re-read | L2-4 | checked |

Lane claims against the diff: "a bare IPv6 literal stored and matched in bracketed form" is borne out for `EgressPolicy`, `WellKnownConfig` and `HttpJwksLoaderConfig` and **not** for `ClientConfiguration` (L2-2); "only a port-carrying
element newly fails startup" is understated (L2-1); "`ClientConfigMapper` makes a port-carrying element fail the mapping" is true in effect and misattributed (L2-3). The `weakened_test` finding the lane reports (the operator-accepted replacement of
`shouldRejectBlankAllowedHost` by `shouldRejectNonBareAllowedHost`) was not examined at the head and is the lane's account.

| Finding | Claim | Verdict | Polarity | Evidence (file:line) |
|---------|-------|---------|----------|----------------------|
| L2-1 | The PR narrative says only a port-carrying element newly fails Quarkus startup. The catch converts every `IllegalArgumentException` from `builder.allowedEgressHost` into a startup `IllegalStateException`, so any non-bare element fails startup: `https://keycloak`, `key_cloak`, a non-ASCII name, `idp.123`, a malformed IPv6 literal, a name with `%`. Blank elements stay dropped | checked | refutes | `IssuerConfigResolver.java:555-563` (re-read) |
| L2-2 | The narrative says a bare IPv6 literal is stored in bracketed form by the builders. `ClientConfiguration` keeps the configured spelling (`List.copyOf` of the raw entries) and only `getEgressPolicy()` canonicalises, so the builders do not all store the same form. The difference is visible through `getAllowedEgressHosts()`, `equals` and `toString`; `ClientConfigurationTest:908-914` pins it | checked | refutes | `ClientConfiguration.java:428-435`, `:452-464` (re-read); `ClientConfigurationTest.java:908-914` (re-read) |
| L2-3 | The narrative attributes the port refusal on the client path to `ClientConfigMapper`. The mapper diff is 11 Javadoc lines and the code at `:114-116` is unchanged; the refusal comes from `ClientConfiguration.validateAllowedEgressHosts` at `builder.build()`. The effect is real (`TokenSheriffClientProducerTest:392`, leaf) | checked | confirms (effect), refutes (attribution) | `ClientConfigMapper.java:114-116` and the diff (re-read) |
| L2-4 | D6 says `test-strategy.adoc` names the new tests. The `CLIENT-30` row names the key, PEM, origin, `ClientConfigurationTest` and `RevocationClientTest` cases and omits `EgressHostValidatorTest`, the `EgressPolicy`, `WellKnownConfig` and `HttpJwksLoaderConfig` builder tests, `IssuerConfigResolverEgressHostTest`, `BackChannelEgressPolicyTest` and the `TokenSheriffClientProducerTest` mapper case, all part of deliverable 4 | checked | refutes | `doc/client/specification/test-strategy.adoc:603-621` (re-read; `EgressHostValidatorTest` appears nowhere under `doc/`) |
| L2-5 | D4(a) asks every refusal class to have a test per call site. At the `EgressPolicy`, `WellKnownConfig` and `HttpJwksLoaderConfig` setters only null, blank, `host:port` and `[::1]:port` are tested (only `EgressPolicy` adds a URL-shaped entry); whitespace, control, non-ASCII and invalid-host entries are tested at the validator unit and at `ClientConfiguration` only. The builders delegate to one rule, so the risk is low | checked (leaf; not re-read) | refutes | `EgressPolicyTest.java:246`, `WellKnownConfigTest.java:126`, `HttpJwksLoaderConfigTest.java:172` |
| L2-6 | AGENTS.md asks the PR body for a reason when a created or grown test class exceeds 500 lines. `HttpJwksLoaderConfigTest` is 537 lines (489 before), `TokenSheriffClientProducerTest` 523 (506 before), `ClientConfigurationTest` 968 (895 before, already over). The PR body gives no reason. The new classes are within the guideline (largest 318) | checked | refutes | `git show` line counts at `dfab40cb` and `aff794eb` (re-counted) |
| L2-7 | The `UncheckedThrowBoundaryTest` edit is explained by deliverable 4: one new `CONSTRUCTION_TIME` entry for `EgressHostValidator#requireBareHost` | checked | confirms | the diff (re-read) |
| L2-8 | Every file in the merge diff is explained by a deliverable; `CLIENT-30` is the next free id; its traceability row sits between `CLIENT-28` and `CLIENT-21..22` (cosmetic) | checked (leaf; not re-read) | confirms | `doc/client/requirements.adoc` ~807, `README.adoc:52`, `:76` |

**Layer 2 verdict:** findings — 8 (L2-1 to L2-6 minor, L2-7 and L2-8 info)

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 | `EgressHostValidator.java:92`, `:158-160` (re-read) | an allow-list entry `[fe80::1%eth0]` (a bracketed zoned IPv6 literal) | refused outright, where the old code stored it as a non-blank entry. Whether `java.net.URI` returns such a host unchanged from `getHost()`, and so whether the old entry could ever have matched, is JDK behaviour that reading cannot settle. If it can match, a previously working entry now fails the build, beyond "blank, null or port" | minor, not shown by reading |
| L3-2 | `PemKeyPairLoader.java:180`, `:184` (re-read) | the path is a symlink to a regular file, or a FIFO or device is swapped in after the `isRegularFile` check | `Files.isRegularFile` follows symlinks, so a symlink to a regular file is admitted (the spec does not forbid it); a special file swapped in after `:180` blocks at the open with no timeout. The size bound itself cannot be defeated, because the read is bounded to bound+1 bytes and takes no size from metadata. The path is operator-supplied, so the effect is a hang with no data exposure | info |
| L3-3 | `PemKeyPairLoader.java:198` (re-read), `:320` (leaf) | any key file | the whole file, including the base64 private-key block, is held in a `String` and a `StringBuilder` that cannot be wiped and stay on the heap until collection; the DER arrays are not zeroed. No log or exception path carries it | info (hardening note) |
| L3-4 | `PemKeyPairLoaderAdversarialTest` (off-curve EC public half), `JwkPublicMembersTest.java:160` (leaf) | a JDK whose `KeyFactory` validates EC points at decode time | the off-curve test expects `EC_KEY_NOT_P256`, which needs `KeyFactory` to accept an off-curve point; `JwkPublicMembersTest` builds its own `FixedEcPublicKey` because the JDK refuses to build such keys. The two assumptions are consistent only if the JDK accepts the point. A validating JDK turns the expected text into `KEY_TYPE_NOT_ALLOWED`; the refusal would hold and the assertion would break | minor, not shown by reading |

**Layer 3 not shown by reading:** L3-1 and L3-4; the green `-Ppre-commit` and clean install (the PR-body Test Plan boxes are unchecked); the Javadoc rule on `KeyPairPolicy.defaults()` and `allows()` (a bare `@return` with no description, whether the doclint in force flags it needs a run);
the `jkt` golden values being captured before the move; and every claim the leaf listed as a lane statement. The leaf read and found sound: the label state machine, the pair probe, the RSA floor, the EC curve and point check, the unmodifiable map, `BackChannelOriginValidator`'s
equivalence with the old private method, `revokeIfSupported`'s delegation, null handling and fixed refusal texts in every builder (none echoes the entry or a cause).

**Layer 3 verdict:** findings — 4 (L3-1 and L3-4 minor, not shown by reading; L3-2 and L3-3 info); no blocker or major

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| AGENTS.md | `CuiLogger` with `%s` only | conforms | leaf: new logging is `RevocationClient.java:162` (re-read, a literal DEBUG) and `IssuerConfigResolver.java:566` (re-read, two `%s`) |
| AGENTS.md | `LogRecord` for INFO/WARN/ERROR documented in `doc/LogMessages.adoc` | conforms | leaf: no `LogRecord` or `LOGGER.(info\|warn\|error)` line in the diff; `LogMessages.adoc` untouched |
| CLAUDE.md Pre-1.0 | no `@Deprecated`, no transitional comments | conforms | leaf (`git diff -G`) |
| AGENTS.md Testing | JUnit 5, AAA, no Mockito/PowerMock/Hamcrest; parameterized for 3+ | conforms | leaf (`git diff -G`) |
| AGENTS.md Test Class Size | at most 500 lines, or a reason in the PR body | deviates | L2-6 (a guideline, not a gate) |
| CLAUDE.md / spec security | refusal texts are fixed and never echo key material, paths, origins or hosts | conforms | `PemKeyPairLoader.java:181-195` (re-read) and the validators return or throw constants; `ClientConfiguration.java:458` names the index only (re-read); `IssuerConfigResolver.java:560-562` chains a fixed-text cause (re-read) |
| spec | no new dependency; `DecryptionKeyLoader` and the Quarkus `jwe` package unchanged | conforms | leaf (`git diff -G pom`; neither file in the stat) |
| spec | D4 default-behaviour change limited to blank, null or port-carrying entries | deviates | L1-2, L2-1 |
| Javadoc rules | class and method Javadoc complete | not shown by reading | L3 note above |
| AGENTS.md | pre-commit sequence, `clean install`, integration tests | not shown by reading | needs a build |

## Metrics and Anomalies

- Tokens: 22,262,128 total (the lane's figure and the archived `metrics.md` total, which spans populations: main and sub-agent for phases 2 to 6; the billing-weighted total is 118,132,500). Phase 6 is 12,572,863 tokens and 3 h 51 min of the 7 h 33 min reported wall time.
- Duration: 27,229 s of wall time (about 7.6 h; the lane's figure and the `metrics.md` total, with 4 h 49 min idle).
- Model and effort: `claude-sonnet-5-5`, `medium`, plugin 0.1.2133 (the lane's figures).
- Anomalies: the functional-review re-run at head `67672997` was waived (`head_binding_waived=functional-review`; the decision log records the basis: that commit only moves two private helpers into `PemKeyPairLoader.PemParser`, the review had passed at `f0f7cf41`, and the leaf found the two helper bodies identical in the diff);
  an operator-accepted `weakened_test` replacement was re-raised at later heads (the lane's account; candidate message `-004`); a formatter-only commit (`f0f7cf41`, one file, 1 insertion and 3 deletions of `import static` lines, re-read by `git show --stat`); the e2e CI job hung after 30 passing tests and needed a re-run of the failed job (the lane's account, not checked); the
  post-merge CI wait ended on the non-gating JMH workflow only.
- `script_failure` clusters (read in the archived plan's work log, `work.log:37`, `:158`, `:243`, 555 lines): three `argparse_rejection`s, all from an invented name: `manage-findings qgate query` (the registered verbs are add, clear, list, resolve, resolve-by-rule, resolve-evidenced), and `manage-solution-outline get-deliverable --number` (the declared flag is `--deliverable-number`) twice,
  09:26:46 and 10:01:01, 34 minutes apart. Recurrences of lesson 2026-11-11-12-003 (not in the lessons store) and of the PLAN-04 and PLAN-07 records.

## Routing and Merge Behavior

- Review: CodeRabbit raised three inline threads (all three read as resolved, each with a reply) and a review body with two nitpicks; Sourcery declined (the diff is over its 150,000-character cap). The lane's account is that all four stored findings were fixed and answered (a trailing-dot host, the final-label rule, a `KeyType` test derivation and the review body's two nitpicks); the three inline fixes are visible as replies at `f0f7cf4`. Re-read through
  `runbook_lane review-gate --pr-number 915`: `unresolved_review_threads` 0, `review_body_findings_total` 5, `review_body_findings_handled` **not re-verified** (the dispositions live in the run's records, which the provider does not reproduce), `merge_allowed: false` with `reason: body_findings_undisposed`. The landing wrote all four review and Sonar facts as `unknown`, so there is no false claim, only a missing fact.
  `ci pr comments` reports 11 comments with `unresolved: 5` beside the gate's 0 unresolved threads; the five are the review-body findings, not threads (the three threads are resolved; counted, not each item reconciled).
- CI/merge: merged by the merge queue as `dfab40cb` on a recorded waiver (the functional-review head binding, above). No rebase conflict is recorded in the landing and none was looked for.
- Sonar: the landing's `sonar_open_new_issues` is `unknown` (the lane's round trip recorded 0 new-code issues and does not count security hotspots). The orchestrator's read (`read_open_issues --pr 915 --project cuioss_TokenSheriff`) is **undecidable** (`count_status_reason: head_revision_unavailable`, analysis revision `67672997`, the PR's last head), so the figure is recorded as `unknown`, not `0`.
- Surface collision: none observed on PLAN-03's side. **Corrected at the PLAN-09 landing (2026-11-17):** this line first said PLAN-03 ran alone because PLAN-09 was never launched. That was wrong: PLAN-09 had a live plan from `2026-11-17T07:57:04Z` (its archived `metrics.md`) and rebased over #915 at its own branch-cleanup gate, which read an overlap without a content conflict (see `landings/PLAN-09.md`). The predicted seven-file overlap (`HttpJwksLoaderConfig`, `IssuerConfigResolver`, `doc/LogMessages.adoc`, `openid-discovery.adoc`, `doc/commons/requirements.adoc`, `BoundedBodyHandler`, `BoundedBodyHandlerTest`) therefore did not turn into a conflict. The merge touches four of the seven
  (`HttpJwksLoaderConfig`, `IssuerConfigResolver`, `openid-discovery.adoc`, `doc/commons/requirements.adoc`) and not `doc/LogMessages.adoc` or `BoundedBodyHandler`, so the declared surface of the pairing over-predicted by three files. PLAN-09 and PLAN-10 now start from a main that holds the merge and re-read their anchors at HEAD.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-03 --status shipped`
- [x] row `pr` stamped — `orchestrator queue --set-row PLAN-03 --field pr --value "#915"`
- [x] row `landing` stamped — `orchestrator queue --set-row PLAN-03 --field landing --value landings/PLAN-03.md`
- [x] row `plan_marshall_plan_id` stamped — `plan-03-client-key-material-config-validators`
- [x] epic.md narrative reconciled (queue annotations, Decisions, Open Defects, Watches)
- [x] one lesson promoted: 2026-11-17-15-001 (an allow-list host validator is derived from `URI.getHost()`); four candidate messages recorded as recurrences
- [x] resume anchor updated; `queue-view.md` regenerated

## Follow-Ups

- PLAN-03's residuals have no staged spec: the test-strategy gap (L2-4), the per-site refusal coverage (L2-5), the three oversized test classes (L2-6), the `ClientConfiguration` spelling difference (L2-2) and whether the user-facing docs state the wider refusal (L1-2, L2-1; both callers' Javadoc does, re-read), plus the two leads not shown by reading (L3-1, L3-4) and L3-2/L3-3. Staging them is the operator's call (recorded in `epic.md` Open Defects). A layer-3 defect is staged only on the operator's word.
- What API Sheriff must re-measure on bump (recorded in `epic.md`): a configured allow-list entry that is not a bare host (a port, a scheme, an underscore, a non-ASCII name, a trailing space after a comma) now fails startup or mapping; `PrivateKeyJwtAuth` and `DpopProofGenerator` do not check the pair or an RSA floor themselves.
- The review of lesson 2026-11-02-14-001 and the missing lessons: see `epic.md` Open Defects.
