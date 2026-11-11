envelope_version=1
sender_type=plan
sender_id=plan-28-epic-cleanup-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-11T11:55:56Z

# Candidate lesson: log-sanitisation sweep left a CWE-117 sink open in the same class

Source: Q-Gate finding d42731 (phase 6-finalize, code-review, error, DpopProofValidator.java:615, resolution taken_into_account; the code review closed at 7d929659)

The deliverable sanitised DPoP proof logs, but `DpopProofValidator.rejectWith` still logged the raw proof `typ`, `alg` and `kty` at WARN. Code review looped back (TASK-019: sanitise the rejectWith log and its thrown message). CodeRabbit later found four more directly thrown DPoP rejections (thumbprint, jti replay, htm, htu) with raw remote text in the exception message (TASK-020).

Possible rule: a log-sanitisation sweep over a class must list every sink in it, meaning each LOGGER call and each exception message built from remote input. Pin the thrown message as well as the WARN text in tests. Sanitising only the sites the request names left two follow-up loops in this run.
