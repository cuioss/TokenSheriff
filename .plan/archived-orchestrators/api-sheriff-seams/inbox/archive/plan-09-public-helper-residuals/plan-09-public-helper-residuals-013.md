envelope_version=1
sender_type=plan
sender_id=plan-09-public-helper-residuals
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-17T16:34:21Z

# Owed architecture hint: maven - frontend build output is classified as a build error by the log parser

**Signal source:** preference-emitter. One (module, finding-class, disposition) pattern cleared the within-plan threshold: module `maven`, finding class `build error [other]`, disposition `suppressed`, recurring 98 times in this plan (threshold 2), all authored by the pipeline's own triage and admitted by the authorship gate.

**Owed call (not executed here, because this step runs after the merge and an `architecture enrich` write would land as an uncommitted diff on main):**

- target `--module`: `maven`
- enrich verb: `best-practice`
- verbatim generalized hint text: `Prefer to suppress build-error findings of category other in maven when the line comes from the frontend part of the build (npm peer-dependency and engine warnings, Jest summary, timing and snapshot lines, WSL path diagnostics): the log parser reads these lines as build errors although the build did not fail, so judge a build by its exit status and its failing-test list, not by the parser's finding count.`

**Why this is a hint and not a lesson:** the recurrence is a standing, project-specific judgement (the same non-issue class suppressed every time), not a defect with a corrective rule for the plan author. The corrective action sits with the log parser: classifying npm `warn` lines and passing Jest summary lines as non-errors would remove the noise at the source and make the hint unnecessary.

**Command for the pickup:**

```bash
python3 .plan/execute-script.py plan-marshall:manage-architecture:architecture \
  enrich best-practice --module maven --practice "<the verbatim text above>"
```
