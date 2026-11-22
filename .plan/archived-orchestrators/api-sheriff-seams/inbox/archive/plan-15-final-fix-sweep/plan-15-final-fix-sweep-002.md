envelope_version=1
sender_type=plan
sender_id=plan-15-final-fix-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-21T12:44:34Z

# Candidate lesson: pre-commit formatter injects a TODO marker at a new multi-catch; the cui-rewrite:disable directive stops it

**Signal:** Q-Gate (6-finalize, code-review)
**Records:** Q-Gate finding 7b6bcf (RawHttpServer.java:194, resolved `fixed` in ed4296fd); decision log 3b9ec8 / fec98a (2026-11-21T11:49, re-fire after 4e1fef61 "the sanctioned cui-rewrite directive and reason comment at RawHttpServer.closeRecordingFailure after the gate formatter injected a TODO marker").

## What happened

`RawHttpServer.closeRecordingFailure` got a broader catch. The project's pre-commit / OpenRewrite formatter (InvalidExceptionUsageRecipe) rewrote the catch site and injected a TODO marker into the source. The marker was committed. Code-review flagged it as "OpenRewrite TODO marker committed ... instead of the sanctioned suppression directive". The fix was the project's `cui-rewrite:disable InvalidExceptionUsageRecipe` directive with a reason comment.

It happened twice. A later change widened the catch to `catch (IOException | RuntimeException)` (review comment b09886). The pre-push gate's formatter injected the marker again, so a comment-only commit 4e1fef61 had to restore the directive. That commit moved the head and started a full review round and gate re-run.

## Why it may be worth carrying

Any new or widened catch of RuntimeException (or a multi-catch that includes it) in this repo trips the recipe during the gate build, and the gate's TODO marker reaches the commit. Putting the `cui-rewrite:disable InvalidExceptionUsageRecipe` directive and its reason comment into the same edit avoids an extra head advance.
