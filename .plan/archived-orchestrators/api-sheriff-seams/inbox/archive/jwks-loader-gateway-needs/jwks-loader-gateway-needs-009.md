envelope_version=1
sender_type=plan
sender_id=jwks-loader-gateway-needs
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-15T11:01:11Z

# Candidate lesson: architecture search rejected --module and a pre-verb --plan-id

Source signal: script-failure cluster `plan-marshall:manage-architecture:architecture`, 3 `[ERROR] ... script_failure` work-log rows, all exit_code=2 argparse rejections.

## Records

- 2026-11-13T13:25:57Z (phase-5-execute, TASK-009): `architecture search --module token-sheriff-validation-quarkus`. `--module` is not declared on `search`.
- 2026-11-13T13:57:03Z (phase-5-execute, TASK-011): `architecture.py --plan-id jwks-loader-gateway-needs search --content --pattern VALIDATION-4`. Rejected.
- 2026-11-13T16:11:23Z (6-finalize security-audit re-fire): `architecture.py --plan-id jwks-loader-gateway-needs search --content --pattern 'scheduledExecutorService\('`. Same rejection.

## Candidate rule

`architecture search --content` takes no `--plan-id` before the verb and no `--module` filter. The agents used the `ci` router's pre-verb `--plan-id` form on a script that does not accept it, which is the mirror of a documented argparse-rejection signature. The same mistake recurred in two different envelopes (execute and security audit), so prose guidance did not stop it. Candidates: the error message could say which flags `search` accepts, or the security-audit and execute-task workflow examples could show the exact canonical form.

Component hint: plan-marshall:manage-architecture.
