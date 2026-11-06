envelope_version=1
sender_type=plan
sender_id=plan-17-javadoc-examples
epic=quality-report
kind=finding
created=2026-11-06T20:45:30Z

## Possible defect found while documenting NonValidatingJwtParser (not verified by a run)

While rewriting the Javadoc for `NonValidatingJwtParser` (PLAN-17, J-6), reading the code showed a size mismatch for JWE tokens. With JWE configured, a five-part token between 8 KiB and 32 KiB passes `TokenStringValidator`, which allows the larger JWE size, and then reaches `NonValidatingJwtParser.decodeInternal`, whose size precondition appears to throw `IllegalArgumentException` instead of a `TokenValidationException`.

This was read from the code and not reproduced, because PLAN-17 is Javadoc-only with no behaviour change and no new tests. It needs a test with a 5-part token of about 10 KiB under a JWE-enabled issuer.

## Other Javadoc inaccuracies left out of PR #878 on purpose

- `BearerTokenProducer.produceBearerTokenResult` (about line 376) still says the token comes only from the Authorization header.
- `NonValidatingJwtParser` (lines 107-109) scopes the size pre-check to "ID-token parsing", but all three pipelines run it.
- `JwksLoaderFactory` (lines 30-31) lists key-feature bullets that no longer match its three explicit methods.
- `doc/validation/threat-model.adoc` still says "blacklist".
- `JwtPropertyKeys` says "preferred and allowed" for the algorithm preferences, which was kept deliberately.
