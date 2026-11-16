envelope_version=1
sender_type=plan
sender_id=client-refresh-verification-discovery-cache
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-15T15:08:30Z

component=plan-marshall:workflow-integration-git
category=anti-pattern
source_plan=client-refresh-verification-discovery-cache
source_record=work-log 4b2000 (2026-11-15T11:55:40Z, 6-finalize code-review loop-back) script_failure script_internal_failure

# Finalize fix commit refused with no_sanction: the commit was attempted without --step or a section-6 record

## What happened

After code-review returned `loop_back` for the CachingDiscoveryResolver race (cdc31c, loop-back iteration
1/5), the fix was built and verified in the worktree. Then `git-workflow` commit was called in
6-finalize without a sanction. The script refused (`reason: no_sanction`, `step: null`): "no step id
from the composed phase-6 manifest, no open loop-back fix task and no review-rubric section-6
inline-correction record". The fix landed afterwards, and functional-review and code-review re-ran
from 11:55:58Z, so a sanctioned route was found. The first attempt still cost a failed call and a
recovery.

## Candidate rule

A source-tree commit inside 6-finalize must carry its sanction from the start. Either pass
`--step {composed phase-6 step id}` (here `code-review`), or record the review-rubric section-6
inline correction first, or open a loop-back fix task to 5-execute. The code-review loop-back
workflow should state which of the three routes an inline finalize fix takes and give the exact
commit invocation. Then the agent does not find out the requirement from the refusal.
