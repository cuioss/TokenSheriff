envelope_version=1
sender_type=plan
sender_id=plan-08-quarkus-config-cdi
epic=quality-report
kind=candidate-lesson
created=2026-11-02T11:41:49Z

# Candidate lesson: review_completeness check called with undeclared --pr-number

**Signal source:** script-failure cluster, work-log `[ERROR] ... script_failure` at 2026-11-02T09:30:50Z (phase 6-finalize, automatic-review iteration 1)
**Notation:** plan-marshall:automatic-review:review_completeness
**Failure kind:** argparse_rejection, exit_code=2

## What happened

The automatic-review envelope called `review_completeness check --pr-number ...`. The `check` verb does not declare `--pr-number`. Its flags are `--plan-id`, `--required-bots`, `--optional-bots`, `--participated-bots`, `--declined-bots`, `--in-progress-bots`, `--refused-bots`, `--refused-causes`, `--refusal-size-caps`, `--unrecognised-refusal-bots`, `--stale-participation-bots`, `--not-triggered`, `--measured-diff-size`, and `--triage-ran`. The first automatic-review dispatch ended. The step was dispatched again (iteration 2 at 09:37) and completed.

## Candidate rule

`review_completeness check` is plan-scoped (`--plan-id`) and takes the observed bot states as flags. The PR number is not an input, because the PR is resolved from the plan. Check whether the automatic-review SKILL.md shows `--pr-number` near the `check` call (for example carried over from the neighbouring `ci pr` verbs). If it does, fix the doc so the canonical invocation is quoted verbatim. This failure cost one full automatic-review re-dispatch (about 7 minutes).
