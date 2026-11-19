envelope_version=1
sender_type=plan
sender_id=plan-12-open-items-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-19T06:30:10Z

# Candidate lesson: platform_runtime subagent dispatch answers prompt_not_found for execution-context-level-N

- Signal: process observation 3 of the run (WARNING lines, decision log)

## What happened

At the first phase-5 envelope, `platform_runtime subagent dispatch` returned `prompt_not_found` for
`execution-context-level-4` ("agent not in marketplace tree"). Every later envelope met the same answer. The orchestrator
dispatched each envelope through the Agent tool (Claude target, spawning tool available) with the same prompt body and
logged a WARNING each time. Ten phase-5 envelopes in total went through the fallback.

## Evidence

- Decision log 295952 (15:18:57Z, envelope 1), a072a8 (20:13:15Z, envelope 8), 7b7759 (20:41:12Z, envelope 9),
  026eb8 (21:30:08Z, envelope 10): "as for envelopes 1-N".

## Observation for the epic

The dispatch seam looks the executor up in the marketplace tree, which a consumer repository does not have; the installed
executor variants were reachable only through the platform's own agent tool.
