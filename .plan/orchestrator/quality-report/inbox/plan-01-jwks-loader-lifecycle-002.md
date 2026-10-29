envelope_version=1
sender_type=plan
sender_id=plan-01-jwks-loader-lifecycle
epic=quality-report
kind=candidate-lesson
created=2026-10-28T20:40:01Z

# Merge-authorization grant only accepts the barrier's own option labels

**Source plan:** plan-01-jwks-loader-lifecycle (PR #830)
**Signal:** pre-merge review barrier / operator escalation
**Suggested component:** plan-marshall:phase-6-finalize (pre-merge barrier) / plan-marshall:manage-status (merge-authorization grant)

## What happened

The pre-merge review barrier blocked and the orchestrator asked the operator how to proceed. The prompt offered a paraphrased option ("Accept gap and merge"). The merge-authorization grant verb refused that answer, because it only accepts the barrier's own option label ("Merge anyway (record reason)"). The operator had to be asked again.

## Corrective rule (candidate)

When the barrier blocks, build the operator prompt from the option labels the barrier itself returns, word for word. Do not paraphrase them. The recorded answer is checked against those labels. A possible tooling fix is for the barrier to emit a ready-to-use prompt-required envelope that carries its labels, so the orchestrator never writes them by hand.
