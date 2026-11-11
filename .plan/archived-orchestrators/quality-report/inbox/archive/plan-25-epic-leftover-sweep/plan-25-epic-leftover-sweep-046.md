envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:27:14Z

component=plan-marshall:automatic-review
category=improvement
created=2026-11-09
source_record=automated-review signal (automatic-review step, PR #883)

# Required review bot refuses a PR over its file cap, which forces a PR split late in finalize

## Observation

CodeRabbit (a required review bot) refused to review the 163-file head of PR #883: "Review skipped: 163 files exceed the limit of 100". The operator decided to split the plan into two PRs. Part 1 (validation, 74 files) became #883, CodeRabbit reviewed that head, and its findings were fixed in-run (TokenBuilder, JwsAlgorithm, package-info and two tests). The skip notice was later accepted as obsolete.

## Candidate rule

Check the diff's file count against each required review bot's file cap before creating the PR (CodeRabbit: 100). When a plan's footprint is over the cap, plan the PR split at outline time (part boundaries by module), not after the bot refuses at finalize.
