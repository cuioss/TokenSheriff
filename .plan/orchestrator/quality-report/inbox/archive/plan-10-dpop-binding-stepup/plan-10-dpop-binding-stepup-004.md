envelope_version=1
sender_type=plan
sender_id=plan-10-dpop-binding-stepup
epic=quality-report
kind=candidate-lesson
created=2026-11-04T20:52:20Z

# DSL-JSON throws an unchecked ArrayIndexOutOfBoundsException on truncated input, escaping the client's IOException contract

**Source**: 6-finalize Q-Gate finding 709c40 (code-review, blocking, reproduced by the verifier per decision a1ec3a); fixed in commit 3f1ab1b. The defect predates this plan.

## What happened

`TokenEndpointClient.parse()` and `errorCode()` caught only `IOException` around DSL-JSON reads. A 200 body ending inside a string escape, for example `{"access_token":"abc\`, makes DSL-JSON throw `ArrayIndexOutOfBoundsException`. That unchecked exception escaped `requestToken` after the grant was redeemed, bypassing the documented `RedeemedResponseException` / `TransportException` contract, and `RefreshFlow` then classified it as PRE_REDEMPTION - a wrong retry decision for a one-time grant. The fix folds the runtime exception into `IOException` at the deserialize helper (the deliberate broad catch is documented with the project suppression marker, commit 25e892e).

## Candidate rule

DSL-JSON (and similar fast parsers) can throw unchecked exceptions on malformed or truncated input, not only `IOException`. Every DSL-JSON read on an untrusted body (token endpoint, JWKS, discovery, introspection) must sit behind one helper that converts any `RuntimeException` from the parser into the module's checked parse failure, and each such seam needs a truncated-escape test. Worth a sweep of the other DSL-JSON call sites in the repository, since this one was pre-existing and found only incidentally.

## Related observation

Decision 56bfbb: deeply nested JSON inside the 8 KiB body bound can overflow a constrained thread stack; also pre-existing and not addressed here.
