envelope_version=1
sender_type=plan
sender_id=plan-10-client-and-cache-residuals
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-18T13:07:40Z

# Script-failure cluster: sonar post_responses argparse rejections (invented flag, then wrong flag position)

**Signal source:** `manage-logging signal-scan`. This message covers notation `plan-marshall:workflow-integration-sonar:sonar`. Work log 10:42:13Z and 10:42:16Z, inside the verification-feedback dispatch. The third clustered notation, `plan-marshall:manage-solution-outline:manage-solution-outline`, failed earlier in the run. This envelope did not read that row's content, so the orchestrator should inspect it in the plan's work log before classifying it.
**Component guess:** plan-marshall:workflow-integration-sonar (post_responses canonical invocation) / plan-marshall:plan-marshall verification-feedback workflow.

## What happened

1. `sonar post_responses --pr-number 920` was rejected because the flag is not declared. The declared flags are `ce-wait-timeout, plan-id, pr, project, severities`. The flag name was paraphrased from the GitHub-provider vocabulary.
2. The retry used `--pr 920`, but placed so that the router parsed it (`sonar.py: error: unrecognized arguments: --pr 920`). The flag belongs to the subcommand and must come after `post_responses`.

Both are recurrence signatures that are already catalogued: verb-paraphrase and verb-scoped-flag position.

## Candidate rule

The verification-feedback workflow, or whichever doc drives the sonar response post, should quote the exact `post_responses` canonical invocation: `--pr N` after the verb. The leaf should not derive it from the GitHub provider's `--pr-number`. Better still, make the flag name the same across the GitHub and Sonar providers so a paraphrase cannot happen.
