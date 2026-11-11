envelope_version=1
sender_type=plan
sender_id=plan-16-keycloak-it-assertions
epic=quality-report
kind=candidate-lesson
created=2026-11-06T06:02:56Z

## Candidate lesson: a post-review nitpick commit re-runs every finalize gate that depends on the head commit (about 1.5 hours)

**Source signal**: Signal 2 (automated review). The fix for finding `4d8748` (CodeRabbit, 4 trivial / low-value nitpicks on PR #868) was pushed as a follow-up commit (589e08a, TASK-8) after review.

**What happened**: The new head commit invalidated every finalize gate bound to the head. The security audit, both reviews, the build gate, CI and the review bots all ran again. That added roughly 1.5 hours of wall-clock time for four test-assertion tightenings that CodeRabbit itself rated "Trivial / Low value".

**Candidate rule**: Decide case by case whether to fix trivial or low-value review-bot nitpicks on the current PR or defer them. Count the cost of re-running the head-bound gates. Options for the orchestrator to weigh:
(1) Batch all review-bot fixes into one commit, and only after every bot has finished, so the gates re-run once.
(2) Defer trivial nitpicks to a follow-up plan or ledger item, and reply to the comment with the reason.
(3) Look at whether the finalize gates could scope their re-run to the changed paths when a fix touches only test sources.
A test-assertion-only diff is a candidate for the narrowest re-run.

**Scope hint for the orchestrator**: phase-6-finalize gate re-run policy and the automatic-review triage disposition (fix now vs. defer). This is a cross-plan process concern, not specific to this repository.
