envelope_version=1
sender_type=plan
sender_id=plan-12-open-items-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-19T06:30:30Z

# Candidate lesson: CodeRabbit edits its summary comment in place after each reply, which reopens the finalize finding

- Signal: process observation 5 of the run (decision log)

## What happened

CodeRabbit keeps one summary comment on the PR and edits it in place whenever the run posts a reply. Each edit changes the
comment's edit term, so the provider re-fetch treats the summary as a new observation and reopens the stored finding,
even when the body is unchanged in substance. In this run the summary finding had to be re-resolved repeatedly; at the
pre-merge barrier the re-fetch reopened finding 3f0c8d again (edited at 06:04:05Z, after the run's own reply) with no
actionable comments and "Merge Risk Minimal", and it was re-resolved as accepted without a respond pass.

## Evidence

- Decision log 28e238 (06:05:05Z): "the provider re-fetch reopened finding 3f0c8d (CodeRabbit summary comment, edited in
  place at 06:04:05Z after our own reply); body unchanged in substance ... count_stored 0: no new comment exists".
- The pr-comment store holds 257 findings for this plan, most of them re-observations of edited comments.

## Observation for the epic

An edit to a bot's summary comment with no substantive change reopens a finding the run already closed, and the run's own
reply is what triggers the edit.
