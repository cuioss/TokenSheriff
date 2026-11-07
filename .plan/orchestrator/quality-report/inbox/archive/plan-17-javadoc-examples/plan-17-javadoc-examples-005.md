envelope_version=1
sender_type=plan
sender_id=plan-17-javadoc-examples
epic=quality-report
kind=candidate-lesson
created=2026-11-06T20:45:29Z

component=plan-marshall:automatic-review
category=improvement
title=With review_rate_window_await=false a required bot's quota refusal has no recovery path inside the step

## Problem

CodeRabbit refused PR #878 on quota. The manifest has `review_rate_window_await: false`, so the step's own wait-and-retrigger recovery is disabled, and the required reviewer was neither participating nor re-triggered. The orchestrator had to wait in a background sleep (about 540 s) and then call `github_re_review re-review` by hand before the review arrived and the pre-merge barrier could pass.

## Rule

When the knob is `false` and a required bot is refused on an awaitable quota, the step should say plainly that it is escalating to the operator or the barrier, rather than leaving the manual re-review to be discovered. A required bot is never demoted to optional as the remedy.

## Evidence

PLAN-17 finalize, automatic-review loop-backs (two), then the manual re-review at head 3b1e11e5.
