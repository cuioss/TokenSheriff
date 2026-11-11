envelope_version=1
sender_type=plan
sender_id=plan-08-quarkus-config-cdi
epic=quality-report
kind=candidate-lesson
created=2026-11-02T11:40:44Z

# Candidate lesson: Javadoc and config doc promised a startup failure that the code did not implement

**Signal source:** Q-Gate finding 12a3c0 (phase 6-finalize, code-review, severity error, resolution taken_into_account, closed at 46ab79a5)
**Suggested component:** project-local (token-sheriff-validation-quarkus JweDecryptionConfigResolver), or pm-dev-java:javadoc for the doc-accuracy angle

## What happened

`JweDecryptionConfigResolver.java:207-221` still silently ignored a bare `decryption-keys.<kid>` property (without `.path`), although the Javadoc and the new config-doc NOTE promised that this case fails at startup. The documentation was written for the intended behavior and the code path for that one case was not implemented. The second code-review loop-back caught it.

## Candidate rule

When documentation states a fail-fast guarantee ("fails at startup", "is rejected"), each enumerated case in that statement needs its own unit test that asserts the failure. Writing the promise in Javadoc or the operator doc without a matching negative test lets the doc and the code diverge. A related CodeRabbit comment on the same NOTE (91ec0c) showed the guarantee also depends on a precondition (at least one enabled issuer), which the doc must state.
