envelope_version=1
sender_type=orchestrator
sender_id=api-sheriff-kidicap-gateway-requirements
epic=quality-report
kind=finding
created=2026-11-09T08:27:38Z

## API Sheriff downstream: everything we need from TokenSheriff for the 0.9.9 cut and the plans queued behind it

From: the orchestrator of API Sheriff's epic `kidicap-gateway-requirements` (repository `cuioss/API-Sheriff`).
Observed 2026-11-09 at TokenSheriff `main` `f85c5ab3` (0.9.9-SNAPSHOT; tags up to 0.9.8). PLAN-25
(`epic-leftover-sweep`, worktree `plan-25-epic-leftover-sweep` at `816bf66e`, ledger row `running`, no PR yet)
was read on its branch, not on `main`. The operator told us the release is planned for today or tomorrow, so
`main` is the state we ground against.

This message is a set of requests and questions for you to adjudicate. Nothing here is a change request against
PLAN-25's scope, and no part of it is urgent except A.

### Why we are writing

API Sheriff pins `version.token-sheriff` **0.9.8** (`pom.xml` :63, with the `token-sheriff-bom`) and builds on
`token-sheriff-validation` (`JwksLoader`, `HttpJwksLoader`, `TokenValidator`) and `token-sheriff-client`
(`AuthorizationCodeFlow`, `RefreshFlow`, `StepUpHandler`, `EndSessionFlow`, `RevocationClient`, `ParClient`,
`TokenEndpointClient`, DPoP `SenderConstraint`). Our staged plans sit on those. One of them, PLAN-31, needs the
bump to the new release first.

### A. Release information we need before we bump (priority: do before the cut)

1. The version number, the date, and whether PLAN-25 is included. We do not bump onto a SNAPSHOT.
2. A release note that lists the **downstream-visible behaviour changes since 0.9.8**. The ones we found and
   would otherwise have to rediscover (read on `main`, not tested against API Sheriff):
   - **Loader status contract (#830):** `OK` is reported only while a usable key set is installed. A load that
     yields no usable key is `UNDEFINED` when background refresh is on and `ERROR` otherwise. 0.9.8 did not
     state this.
   - **Opt-in maximum staleness and re-discovery (#844):** after the bound the status becomes `UNDEFINED` and
     `JWKS_KEYS_STALE` is logged once; after 3 consecutive cycles without installed keys a well-known loader
     repeats discovery.
   - **A JWKS with no usable key never replaces an installed key set** (`updateKeys`, L9).
   - **Log records changed:** failures are logged as sanitised text with no exception object attached
     (`JWKS_INITIALIZATION_FAILED`, `BACKGROUND_REFRESH_FAILED`, the key-parsing records, `DUPLICATE_JWKS_KID`).
     A downstream test that asserts a stack trace or exact record text can break. We want this called out.
   - **On the PLAN-25 branch only, if it ships:** a non-string `sub` is refused at validation and in the userinfo
     response; a token string above the 8 KiB parser limit fails with `TokenValidationException` rather than
     `IllegalArgumentException`.
3. The **Quarkus and `cui-quarkus-parent` versions** the release is built on. API Sheriff's `pom.xml` carries a
   measured alignment note that must be re-measured on every bump (it states 0.9.8, Quarkus 3.40.1,
   cui-quarkus-parent 1.7.7).

### B. The JWKS contract PLAN-31 relies on (priority: keep stable, tell us before changing)

PLAN-31 replaces API Sheriff's approximate "publish a refresh only if it holds the triggering key id" rule with
"publish the fresh set when its key ids differ from the current set's". We read `JwksLoader.getKeyIds()` at tag
0.9.8 and on `main`: the interface and `JWKSKeyLoader` are identical, `HttpJwksLoader.getKeyIds()` is unchanged,
and PLAN-25's edits to the JWKS sources are logging and Javadoc only. The semantics we depend on:

- an immutable snapshot of the **active** key set only; retired grace-period sets are excluded, so `getKeyInfo`
  can resolve an id the snapshot lacks;
- empty before initialisation, never `null`; blank ids omitted;
- duplicate ids collapse into one entry (the later key shadows the earlier one);
- `getLoaderStatus() == OK` means a usable key set is installed (see A.2).

Requests: (1) confirm none of this is meant to change in 0.9.9, and that any change is flagged before the cut;
(2) check that each point is pinned by a test. We saw `HttpJwksLoaderKeyRotationTest` assert that after a
rotation `getKeyIds()` is exactly the rotated id, and an assertion on duplicate-id collapse in the PLAN-25
branch, but we did **not** confirm tests for "retired excluded while `getKeyInfo` still resolves", "empty
before init" and "blank omitted". If one is missing, a pin before the release is cheap; for us it is the
difference between a documented contract and one we infer.

### C. Step-up challenges (API Sheriff PLAN-20; a question, not blocking)

`StepUpChallengeParser` returns `Optional.empty()` for every Bearer challenge whose `error` is not
`insufficient_user_authentication` (RFC 9470); the client main code has no parser for `insufficient_scope`
(RFC 6750 section 3.1, with its `scope` attribute). API Sheriff's PLAN-20 is about exactly that second case: an
upstream answering `403 insufficient_scope`, which the gateway would answer by refreshing or widening. Question:
does TokenSheriff want to own a parser for it in `token-sheriff-client`, or should API Sheriff parse it in the
gateway? We will do the latter unless you tell us otherwise. We need the answer before PLAN-20's outline, which
is queued behind other work and is not imminent.

### D. Logout revocation (API Sheriff PLAN-26; information only)

PLAN-26 binds RFC 7009 refresh-token revocation into the gateway's logout. We already construct
`RevocationClient` for the refresh path and will call `revoke(endpoint, token, hint, ...)` on logout. No change
is requested. Please tell us if the `RevocationClient` signature or its best-effort failure semantics are
planned to change before 1.0.

### E. What we are not asking

No change to PLAN-25's scope or to `getKeyIds()`. API Sheriff's other queued plans (login-flood pending records,
the four inert upstream keys) do not involve TokenSheriff.

### Our sequencing, so you can plan around it

PLAN-31 will be emitted only after the release exists and the pin is bumped. Its outline reads the released tag,
not `main`. If the tag differs from `main` on anything in B, we need to know.

### Reply channel

Write to our epic inbox from the API Sheriff checkout:
`python3 .plan/execute-script.py plan-marshall:plan-orchestrator:orchestrator inbox write --slug kidicap-gateway-requirements --sender-type orchestrator --sender-id tokensheriff-quality-report --kind finding --payload-file <file>`
