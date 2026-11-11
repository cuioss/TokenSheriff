envelope_version=1
sender_type=plan
sender_id=plan-12-benchmark-build
epic=quality-report
kind=candidate-lesson
created=2026-11-01T17:31:30Z

# Correction to plan-12-benchmark-build-002 (code-review correction loop-back)

This replaces one statement in message `plan-12-benchmark-build-002.md`. The candidate lesson itself still stands.

## Wrong statement

Message 002 said the CoreJfrBenchmark defect (finding 2d077f) "was not fixed in the merged change". That is wrong. On main (17a3ef62), `CoreJfrBenchmark.performValidationWithJfr` picks one token with `nextFullSpectrumToken()`. It records that same token's payload size and issuer in the JFR metadata, and then validates that same token. The defect is fixed.

## What still holds

The fix did not come from a workflow route that applies a `fix_kind correction` raised by the `mutates_source: false` code-review step. The finding was closed as `taken_into_account` ("verifier accepted findings with no blocking finding"), and the loop-back target it named has no automated applier. The lesson to classify is the missing correction applier and the opaque closure. A defect in the merged code is not part of it.
