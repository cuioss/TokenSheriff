envelope_version=1
sender_type=plan
sender_id=client-refresh-verification-discovery-cache
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-15T15:07:24Z

component=plan-marshall:phase-6-finalize
category=improvement
source_plan=client-refresh-verification-discovery-cache
source_record=work-log: functional-review/code-review dispatch rows 11:39-13:44Z

# Functional/code review delta and closing rounds re-run on every fix commit because each advances HEAD

## What happened

In 6-finalize, `default:functional-review` and `default:code-review` were each executed several times in
one run: the initial pass, the pass after the code-review loop-back fix (cdc31c), and again after the
TASK-011 loop-back from the CodeRabbit fix (finalize re-entered "from the start at HEAD 36b61921").
Each execution itself dispatched two envelopes (delta round plus closing round). Counting the
work-log DISPATCH rows, functional-review dispatched 7 times and code-review 6 times; the review
rounds were re-run four times in total because each fix commit moved HEAD and invalidated the prior
review's HEAD binding.

## Cost

Each re-run costs a level-3 envelope pair (roughly 1.5-6 minutes each), plus the pre-push quality gate
re-running a ~30 minute whole-tree verify. The later re-reviews over a one-file test fix (TASK-011
touched only `TokenSheriffClientDiscoveryCacheTest.java`) found nothing new.

## Candidate rule

Scope a finalize re-review to the delta since the last reviewed HEAD when the new commits are a
narrow fix whose footprint is disjoint from the reviewed production surface (for example test-only, or a
single file already reviewed), and carry the earlier closing verdict forward for the unchanged
remainder, instead of re-running the full delta+closing pair. Keep the full re-run only when the fix
touches production files outside the previously-reviewed delta.
