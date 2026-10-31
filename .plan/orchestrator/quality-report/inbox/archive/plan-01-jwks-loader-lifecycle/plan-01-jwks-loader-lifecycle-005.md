envelope_version=1
sender_type=plan
sender_id=plan-01-jwks-loader-lifecycle
epic=quality-report
kind=candidate-lesson
created=2026-10-28T20:40:25Z

# Argparse rejection: `manage-findings query` is not a registered verb

**Source plan:** plan-01-jwks-loader-lifecycle
**Signal:** script-failure cluster (notation plan-marshall:manage-findings:manage-findings)
**Suggested component:** plan-marshall:manage-findings

## What happened

At 14:16:00Z, during the finalize security-audit step, a call to `manage-findings query ...` exited with code 2:

```
`query` is not a registered verb for `plan-marshall:manage-findings:manage-findings`: ['add', 'assessment', 'field', 'get', 'ingest', 'list', 'promote', 'qgate', 'resolve', 'stamp-fix-commit']
```

## Corrective rule (candidate)

The read verb is `list`, with `--type`, `--resolution`, `--include-qgate` and similar filters, or `qgate list --phase P` for Q-Gate findings. The verb name `query` probably comes from the internal function names `query_findings` and `query_findings_unified` that the SKILL.md mentions. This is the verb-paraphrase recurrence signature. Consider whether the security-audit workflow text names a `query` operation that an agent could copy as a verb.
