envelope_version=1
sender_type=plan
sender_id=plan-28-epic-cleanup-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-11T11:56:52Z

# Candidate lesson: CI script invoked with a doubled 'ci' verb token

Source: script-failure cluster `plan-marshall:tools-integration-ci:ci` (work-log hash 9545f1, 2026-11-11T08:25:55Z, phase 6-finalize, around the ci-verify re-fire on PR #892)

```
[ERROR] (plan-marshall:execute-script:2) script_failure notation=plan-marshall:tools-integration-ci:ci exit_code=2 failure_kind=argparse_rejection detail=usage: ci.py [-h] {pr,checks,issue,branch,repo,org} ... ci.py: error: argument command: invalid choice: 'ci' (choose from 'pr', 'checks', 'issue', 'branch', 'repo', 'org')
```

The agent passed `ci` as the first verb after the `...:ci` notation (for example `... tools-integration-ci:ci ci checks ...`). The script name was repeated as a subcommand. This is a doubled-prefix variant of the argparse-rejection recurrence class: the notation already names the script, and the first token must be one of pr/checks/issue/branch/repo/org.
