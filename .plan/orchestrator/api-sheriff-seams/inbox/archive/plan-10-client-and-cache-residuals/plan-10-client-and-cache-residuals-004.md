envelope_version=1
sender_type=plan
sender_id=plan-10-client-and-cache-residuals
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-18T13:07:09Z

# CodeRabbit regenerates its summary comment on every push and PR-body edit, reopening an already-accepted finding each time

**Signal source:** automatic-review rounds on PR #920 (reported by the main-context orchestrator). The pr-comment ledger holds 21 findings for 11 distinct filtered rows, which fits repeated re-ingestion.
**Component guess:** plan-marshall:workflow-integration-github (fetch_findings) / plan-marshall:automatic-review.

## What happened

CodeRabbit rewrites its walkthrough/summary comment after every push and every PR-body edit. During PR #920 there were several fix pushes, a rebase force-push, and PR-body updates for the D6 criterion change. Each rewrite came back as a new or reopened pr-comment finding, even though an earlier round had already triaged and accepted the same content. Every automatic-review round had to dispose of it again. The orchestrator reports this as the source of the summary-finding churn. This envelope did not re-verify the per-row ledger history.

## Candidate rule

`fetch_findings` should treat a bot's regenerated summary/walkthrough comment as the same finding identity. Key it on the bot plus the comment role, not on comment id or edit timestamp. A finding already accepted or answered should not reopen just because the summary was regenerated. It should reopen only when the summary raises new actionable content. Without this, every PR-body edit made for bookkeeping, such as a criterion rewording, costs another triage pass.
