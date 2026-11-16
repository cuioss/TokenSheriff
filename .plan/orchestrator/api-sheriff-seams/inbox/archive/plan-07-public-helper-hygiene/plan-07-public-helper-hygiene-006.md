envelope_version=1
sender_type=plan
sender_id=plan-07-public-helper-hygiene
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-15T22:31:45Z

# Candidate lesson: router flag --plan-id passed to manage-architecture, which rejects it

**Source signal**: script failure cluster, notation `plan-marshall:manage-architecture:architecture` (failure_kind=argparse_rejection, exit_code=2).

## Record

Work log 2026-11-15T19:43:26Z, during the wait-region unified triage dispatch (verification-feedback, PR review findings):

```
architecture.py --plan-id plan-07-public-helper-hygiene search --content --pattern extractTokenFromCookieHeader
```

## For classification

A triage leaf put `--plan-id` before the `search` verb, in the router position the `ci` script uses, on a script that does not take `--plan-id` there. This matches the documented "router-scoped vs verb-scoped --plan-id" recurrence signature: `--plan-id` was added by habit instead of from the script's own canonical-invocation block. The search was a content sweep for the cookie-extraction helper while triaging finding bd25ca.
