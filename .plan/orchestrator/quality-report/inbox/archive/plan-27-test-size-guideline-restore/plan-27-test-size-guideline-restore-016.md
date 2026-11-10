envelope_version=1
sender_type=plan
sender_id=plan-27-test-size-guideline-restore
epic=quality-report
kind=candidate-lesson
created=2026-11-10T09:10:35Z

# Candidate lesson: invented --limit flag on manage-findings list

- Source signal: script failure cluster `plan-marshall:manage-findings:manage-findings` (`[ERROR] ... script_failure`, exit_code=2, failure_kind=argparse_rejection), work-log entry `6ef3b9` at 2026-11-10T08:27:07Z
- Plan: plan-27-test-size-guideline-restore (PR #887)
- Component: plan-marshall:manage-findings

## Record

```
`--limit` is not declared for `plan-marshall:manage-findings:manage-findings list`: ['any-checkout', 'author', 'bot-kind', 'component', 'file-pattern', 'include-qgate', 'kind', 'plan-id', 'preference-admissible', 'promoted', 'resolution', 'type']
```

## Context

Raised just after sonar-roundtrip completed, before branch-cleanup (likely the dispatcher's lessons-capture signal gathering or a pre-merge findings read). `manage-findings list` has no paging flag; `manage-logging read` does have `--limit`, which is the likely source of the paraphrase.

## Observation for the orchestrator

Instance of the known "never invent script flags" recurrence class (flag borrowed from a sibling script). Possible lesson: either add a `--limit` to `manage-findings list` (its pr-comment output is very large - 51KB for this plan) or document in the canonical block that it has none.
