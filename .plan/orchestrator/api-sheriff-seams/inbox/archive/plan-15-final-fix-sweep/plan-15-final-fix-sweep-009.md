envelope_version=1
sender_type=plan
sender_id=plan-15-final-fix-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-21T12:44:52Z

# Candidate lesson: every head advance after the reviews re-fires both reviews and a 30-40 minute gate

**Signal:** automated review (automatic-review step state across re-fires)
**Records:** work log 5e3fbd (09:20:40, loop-back 1/5, "operator chose to re-run all head-dependent steps over the new commit"), dde707 (11:22:42, loop-back 2/3); decision log 3b9ec8 / fec98a (11:49, "Re-fire after 4e1fef61 (comment-only ...): full round, Author DISPATCH"); automatic-review completed `done` four times (09:17:07, 09:55:51, 11:18:41, 12:16:17); pre-push-quality-gate step spans 08:45:30-08:53:52, 09:26:09-09:34:21, 10:24:50-11:04:37, about 11:31-12:03:03; work log 98b723 (sourcery optional, `refused_hard` quota).

## What happened

After the first review round, three more head advances happened: 38a6e041 (Javadoc fix), e959d789/641b4511/be9a2550 (merge-queue flake and review fixes), and 4e1fef61 (a comment-only directive restore). Each one re-ran functional-review and code-review in full, plus the pre-push quality gate, CI and automatic-review. The later gate runs took about 32-40 minutes each, because the doc/*.adoc footprint resolved to no module and the gate ran whole-tree (warning f501d6). A comment-only commit cost the same full round as a code change. Sourcery was optional and refused on quota every time, so CodeRabbit was the only review bot that took part.

## Why it may be worth carrying

Late, small commits (Javadoc wording, a suppression directive) are the costliest kind in 6-finalize. Batching every known follow-up into one head before re-entering the gate would save rounds. So would scoping re-fires by what the commit touched (comment-only vs code).
