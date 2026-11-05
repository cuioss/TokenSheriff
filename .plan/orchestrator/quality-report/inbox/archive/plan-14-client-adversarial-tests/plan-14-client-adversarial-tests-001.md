envelope_version=1
sender_type=plan
sender_id=plan-14-client-adversarial-tests
epic=quality-report
kind=candidate-lesson
created=2026-11-05T13:00:06Z

## Candidate lesson: argparse rejections from invented script flags/verbs

**Signal source:** script-failure clusters (3 distinct failing notations in the run).

**Observation:** During plan-14 (client adversarial tests) several script calls were rejected at argparse level (exit 2) because the agent paraphrased the surface instead of quoting it:

- `manage-findings` was called with a `query` verb that the script does not declare (the canonical read verbs are `list` / `get` with filter flags).
- `architecture search` was called with flags not declared by its parser (the content sweep is `architecture search --content --pattern P`).

**Shape:** defect + corrective action (verb-paraphrase / undeclared-flag recurrence signature, already catalogued in `persona-plan-marshall-agent` agent-behavior-rules "Never invent script subcommands").

**Suggested corrective rule:** quote verb and flag names verbatim from the script's canonical-invocations block or `--help` before the first call; treat a recurrence as evidence the canonical block is not surfaced at the call site of the workflow doc that prompted the call.

**Candidate component(s):** `plan-marshall:manage-findings`, `plan-marshall:manage-architecture` (classification deferred to orchestrator).
