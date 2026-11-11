envelope_version=1
sender_type=plan
sender_id=plan-28-epic-cleanup-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-11T11:57:00Z

# Candidate lesson: manage-findings called with an invented 'query' verb

Source: script-failure cluster `plan-marshall:manage-findings:manage-findings` (work-log hash b83704, 2026-11-11T08:47:34Z, phase 6-finalize, just before the verification-feedback respond pass on PR #892)

```
[ERROR] (plan-marshall:execute-script:2) script_failure notation=plan-marshall:manage-findings:manage-findings exit_code=2 failure_kind=argparse_rejection detail=`query` is not a registered verb for `plan-marshall:manage-findings:manage-findings`: ['add', 'assessment', 'field', 'get', 'ingest', 'list', 'promote', 'qgate', 'resolve', 'stamp-fix-commit']
```

Verb paraphrase: the agent used `query` where the canonical read verb is `list` (with filters) or `get`. This is the verb-paraphrase recurrence class.

Cross-cluster note: all four script failures in this run (manage-references, manage-solution-outline, tools-integration-ci, manage-findings) are argparse rejections from invocations not copied from canonical blocks. None of them blocked progress, and each was retried successfully.
