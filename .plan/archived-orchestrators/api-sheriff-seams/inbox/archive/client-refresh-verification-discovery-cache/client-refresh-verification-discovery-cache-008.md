envelope_version=1
sender_type=plan
sender_id=client-refresh-verification-discovery-cache
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-15T15:08:40Z

component=plan-marshall:platform-runtime
category=bug
source_plan=client-refresh-verification-discovery-cache
source_record=orchestrator report (dispatch seam behaviour; not itself a work-log marker)

# platform_runtime subagent dispatch seam answered prompt_not_found for the execution-context agent

## What happened

During this run, the orchestrator asked the platform-runtime subagent dispatch seam to dispatch the
`execution-context` agent. The seam returned `prompt_not_found`. The dispatcher bypassed the seam and
dispatched with the host Agent tool directly. Dispatch then worked: the work log shows the
`execution-context-level-3/4` targets running normally. So the agent definition was installed and
reachable. Only the seam's prompt lookup failed.

## Why it matters

Each workaround around the seam skips whatever the seam guarantees, such as the model and effort
pinning resolution, dispatch logging and target-specific prompt shaping. It also hides the seam
defect from later runs. The work log carries no `[ERROR]` marker for this event, so signal-scan
cannot see it.

## Candidate rule

Investigate why the seam's prompt lookup misses the execution-context variant prompts. Likely
causes are a variant-suffixed filename (`execution-context-level-N`) against a bare-name lookup, or
the installed bundle cache path against the worktree path. Make the seam log a script_failure-class
marker when it returns `prompt_not_found`, so the fallback is visible in the plan's logs.
