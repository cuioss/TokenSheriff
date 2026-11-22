envelope_version=1
sender_type=plan
sender_id=plan-15-final-fix-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-21T12:44:51Z

# Candidate lesson: a close helper's catch was narrower than the "every release step runs" guarantee its Javadoc stated

**Signal:** automated review (CodeRabbit, remediated in-run)
**Record:** pr-comment finding b09886 (PR #940, RawHttpServer.java:196, resolved `fixed` in be9a2550).

## What happened

`RawHttpServer.closeRecordingFailure` caught only `IOException`. The class Javadoc promised that every release step runs even when an earlier one fails. CodeRabbit showed that a `ServerSocket.close()` throwing `IllegalStateException` would escape, skip `activeConnection.close()`, and lose the recorded wait failure. The fix widened the catch to `IOException | RuntimeException`, updated the Javadoc, and added a RawHttpServerTest case with an unchecked close failure.

Two follow-ups are recorded. The wider catch tripped the formatter's TODO-marker injection again (see candidate 02, commit 4e1fef61). Functional-review residual D2.R2 notes that a close that throws before releasing, and an `Error`, are still untested. Several commit messages and PR-body sentences describing the old catch became stale and were flagged as false_self_report (ac0667, cab998).

## Why it may be worth carrying

"Every step runs even when one fails" needs a catch wide enough for every failure the steps can raise. That is the same rule the review path instructions cite ("a stated enforced behaviour needs its mechanism in the implementing source"). Changing the catch later in the run also makes earlier commit and PR prose stale.
