envelope_version=1
sender_type=plan
sender_id=plan-07-jwks-staleness-timeouts
epic=quality-report
kind=candidate-lesson
created=2026-10-30T16:55:54Z

# Candidate lesson: installed executor scripts lag the skill docs the agents follow

**Source signal**: script-failure clusters (argparse rejections) during phase 6, plus a doc/script contradiction logged by automatic-review.

**Component**: plan-marshall executor/bundle version parity - `plan-marshall:automatic-review:review_completeness`, `plan-marshall:phase-6-finalize:pre_merge_gate`, automatic-review recovery-action script; marshall-steward executor regeneration.

## What happened

Agents followed the loaded skill docs and called verbs that the installed scripts do not register:

- `review_completeness roster` rejected 6 times (10:45:36Z, 11:39:23Z, 12:53:34Z, 13:43:19Z, 14:01:20Z, 15:07:56Z): "`roster` is not a registered verb ... ['check', 'deficit', 'size-caps', 'trigger-bot']".
- `pre_merge_gate record-merge` rejected at 16:53:34Z: "`record-merge` is not a registered verb ... ['merge']" - the merge record step of branch-cleanup could not run.
- Decision log 15:08:41Z (54b6c8): the installed recovery-action returned `close_and_reopen` with a review on record, "contradicting the skill doc (review on record -> generate_trigger)"; the agent overrode the script by following the doc.
- Finalize sync-baseline 10:03:47Z: executor drift probe returned `drift=unknown, regenerated=false` (marketplace anchor did not resolve), so the drift was never detected.
- In this same lessons-capture dispatch, skills loaded from cache 0.1.2037 while `resolve-workflow` returned the 0.1.2040 workflow doc - mixed bundle versions inside one envelope.

## Candidate rule

A plan run must not mix skill-doc and script versions. Either the finalize sync-baseline drift probe must yield a definite verdict (and regenerate the executor) for consumer repositories without a marketplace tree, or the dispatcher must fail fast with an explicit "executor older than skill docs" signal on the first unregistered-verb rejection of a documented verb, instead of letting each step degrade silently (repeated rejections, agent overriding script output).

## Evidence pointers

- Work log ERROR lines hash 40a759 (x6) and d354ee.
- Decision log 54b6c8 (recovery-action contradiction), work log f18a9b (drift=unknown).
