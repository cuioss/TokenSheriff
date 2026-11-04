envelope_version=1
sender_type=plan
sender_id=plan-09-dpop-parser-hardening
epic=quality-report
kind=candidate-lesson
created=2026-11-04T12:32:47Z

# Candidate lesson: inserting a pre-check into a verification pipeline inherits the preconditions of the steps it now precedes

**Source plan:** plan-09-dpop-parser-hardening (PR #851)
**Signal:** review-bot findings d0fb52 (coderabbit inline, `SignatureVerificationUtilTest.java:155`, fixed by TASK-14) and 90282a item 1 (coderabbit review-body nitpick, check ordering, fixed by TASK-14)
**Suggested component:** pm-dev-java:java-security (or project convention for `SignatureVerificationUtil`)

## What happened

The plan added `checkEcdsaComponentRange` (the ECDSA r/s range check against CVE-2022-21449) to `SignatureVerificationUtil.verifySignature`. Two defects followed from where the check was placed:

1. **Lost null precondition.** The new check read `signatureBytes.length` before any null check. Before the change, a null signature was rejected with `SignatureException("Signature cannot be null")` by the format converter that ran first. After the change, a null signature with an EC key threw `NullPointerException`, which breaks the public method's declared `SignatureException` contract.
2. **Unreachable guard.** The check ran after `verifier.initVerify(publicKey)`. On SunEC, `initVerify` already rejects an RSA key, so the `instanceof ECPublicKey` guard inside the new check never ran. `shouldRejectNonEcKey` passed without exercising it.

TASK-14 moved the check ahead of `initVerify` and added an explicit null check plus a regression test.

## Reusable rule

When inserting a new validation step ahead of, or between, existing steps:

- List the preconditions the downstream steps used to enforce implicitly (null, length, type), and either keep them or enforce them yourself before reading the input. Keep the same exception type and message.
- Place the check before any provider or JCA call that could reject the same input first. Otherwise the guard's own branches cannot be reached, and their tests pass for the wrong reason.
- Add a test for null and wrong-type inputs on the public entry point, not only for the new rejection.
