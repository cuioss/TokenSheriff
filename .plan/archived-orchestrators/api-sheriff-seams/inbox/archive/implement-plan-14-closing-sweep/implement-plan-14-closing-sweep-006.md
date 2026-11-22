envelope_version=1
sender_type=plan
sender_id=implement-plan-14-closing-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-20T15:11:37Z

# Candidate lesson: post-merge ci checks wait --adaptive waits on non-gating workflows past the 600s cap

- Component: plan-marshall:tools-integration-ci
- Suggested category: bug
- Observed in plan: implement-plan-14-closing-sweep (verified in-session)

## Observation

After merge, `ci checks wait --adaptive` on the merge commit also waits for non-gating post-merge
workflows (here a JMH benchmark run). Those runs take far longer than the gating checks, so the
synchronous call exceeds the 600s Bash tool ceiling and the wait never returns a verdict inside one
call.

## Suggested correction

Restrict the post-merge wait to the gating/required check set (or accept an exclude/required-only
filter), and bound the adaptive budget by the harness bash-timeout ceiling so the call returns a
partial verdict instead of being killed.
