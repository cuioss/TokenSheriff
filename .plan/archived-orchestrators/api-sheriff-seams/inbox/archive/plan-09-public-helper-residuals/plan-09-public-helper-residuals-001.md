envelope_version=1
sender_type=plan
sender_id=plan-09-public-helper-residuals
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-17T16:31:11Z

# Candidate lesson: invented `query` verb on manage-findings (argparse rejection, twice)

**Signal source:** script-failure cluster `plan-marshall:manage-findings:manage-findings` (failure_kind=argparse_rejection, exit_code=2).

**Records:**
- 2026-11-17T08:19:04Z (4-plan Q-Gate validation dispatch): `` `query` is not a registered verb for `plan-marshall:manage-findings:manage-findings qgate`: ['add', 'clear', 'list', 'resolve', 'resolve-by-rule', 'resolve-evidenced'] ``
- 2026-11-17T15:35:22Z (6-finalize, after automatic-review): `` `query` is not a registered verb for `plan-marshall:manage-findings:manage-findings`: ['add', 'assessment', 'field', 'get', 'ingest', 'list', 'promote', 'qgate', 'resolve', 'stamp-fix-commit', 'stamp-pr'] ``

**Observation:** Two different envelopes, seven hours apart, made up the same verb `query`, once under the `qgate` noun and once at the top level. The read verbs are `list` (and `get` for a single finding). This is the verb-paraphrase pattern from the "Never invent script subcommands" rule.

**Possible corrective action (for the orchestrator to judge):** In the workflow docs that read findings (q-gate-validation, the post-automatic-review triage path), name the exact `list` invocation next to the read step. Or have manage-findings take `query` as an alias, or suggest `list` when it rejects `query`.

**In-run impact:** None lasting. Each caller retried with the correct verb.
