envelope_version=1
sender_type=plan
sender_id=plan-06-callback-logout-hardening
epic=quality-report
kind=candidate-lesson
created=2026-10-30T19:09:15Z

component=plan-marshall:phase-6-finalize
category=bug
created=2026-10-30

# Merge-time HEAD binding refuses operator re-run waivers that the step records had accepted

## Observation

At head aa72e8cb (a test-only commit after the gated head e22e62f1), the operator waived the re-run of finalize-step-security-audit and functional-review. The step records accepted the waivers and the pipeline went on to merge routing. Then `pre_merge_gate merge` refused. Decision log 15:35:47Z:

> Pre-merge HEAD binding: merge_candidate_not_gated - candidate aa72e8cb, ungated steps finalize-step-security-audit and functional-review (recorded head e22e62f1 for both); merge blocked, no dispatch, nothing enqueued. Operator's earlier re-run waivers do not satisfy the merge-time head binding.

Both steps then had to be re-run for real at 18:30-18:37 (about three hours later, behind the CodeRabbit wait). Only after that did the merge go through.

## Defect shape

Two gates disagree about what a waiver means. The step-record layer treats an operator waiver as satisfying the head re-fire. The merge-time HEAD binding only accepts a step recorded at the candidate head. The operator gets a "yes" early and a "no" at the last gate, after the merge lock was already taken.

## Suggested direction

- Do one of these two, not both. (a) Let the merge-time binding honour a recorded operator waiver bound to the candidate head (with the waiver reason stored on the authorization record). (b) Refuse the waiver at the point it is offered, with the consequence stated: "this step must re-run at {head} before merge".
- Run the head-binding check before taking the merge lock and before the pre-merge barrier prompt, so a doomed merge is reported before any operator merge-anyway choice.
