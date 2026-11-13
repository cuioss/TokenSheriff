envelope_version=1
sender_type=plan
sender_id=plan-05-validation-public-seams
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-13T06:31:31Z

component=plan-marshall:workflow-integration-github
category=bug
created=2026-11-13
source=qgate 5-execute d803c2 (resolution accepted by operator)

# fetch_findings self-response-loop guard fires on CodeRabbit's in-place-edited summary comment

## What happened

`github_pr fetch_findings` on PR #899 reached `count_self_response_current_cycle=3` (bound 3;
`count_skipped_self_response=4` over the PR history) and raised an operator-decision finding. The loop was not real:
CodeRabbit edits its summary comment in place, so each pass looked like reviewer activity with no new reviewer comment,
and the guard counted the plan's own replies as a non-converging cycle. At the time every reviewer thread was answered,
0 pr-comment and sonar findings were pending, CodeRabbit's review covered head 2c167f4e, and CI was green. The operator
accepted it as benign.

## Proposed rule

The self-response-loop guard should check whether any actionable finding is still pending, or whether the only reviewer
activity is an in-place edit of a summary/review-status comment, before it escalates. An edit-only bump on a bot summary
comment should not count as a new cycle.
