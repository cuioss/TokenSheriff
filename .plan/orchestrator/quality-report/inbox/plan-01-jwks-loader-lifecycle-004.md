envelope_version=1
sender_type=plan
sender_id=plan-01-jwks-loader-lifecycle
epic=quality-report
kind=candidate-lesson
created=2026-10-28T20:40:18Z

# Argparse rejection: `ci pr list --state merged` is not a valid state

**Source plan:** plan-01-jwks-loader-lifecycle
**Signal:** script-failure cluster (notation plan-marshall:tools-integration-ci:ci)
**Suggested component:** plan-marshall:tools-integration-ci

## What happened

At 13:03:01Z, during ci-verify triage (verification-feedback dispatch), a call to `ci pr list --state merged` exited with code 2:

```
ci.py pr list: error: argument --state: invalid choice: 'merged' (choose from 'open', 'closed', 'all')
```

## Corrective rule (candidate)

`ci pr list --state` accepts only `open`, `closed` and `all`. GitHub's own `merged` state has no equivalent. To find merged PRs, use `--state closed` and check the merge field, or use `pr view`. This is the verb-paraphrase signature: the flag value came from GitHub CLI habits, not from the script's `--help`.
