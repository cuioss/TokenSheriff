envelope_version=1
sender_type=plan
sender_id=plan-11-audience-semantics
epic=quality-report
kind=candidate-lesson
created=2026-11-04T17:26:53Z

# Candidate lesson: stand-alone Maven test-compile in the pre-push gate cannot succeed in this multi-module reactor

**Source**: run event reported by the orchestrator during finalize of plan-11-audience-semantics (PR #853, merged as a9ce05df). Not one of the three forwarded signal counts; emitted because the orchestrator flagged it as lesson-worthy.

## What happened

The pre-push gate ran a stand-alone Maven `test-compile` against the reactor. In Token-Sheriff, downstream modules depend on the validation module's test-jar, which only exists after the `package` phase. A `test-compile`-only invocation therefore cannot resolve that artifact and fails. The failure was parsed and auto-filed as 120 build-error findings, which blocked the first merge dispatch until all of them were resolved as rejected. The operator decided the full `verify` run already covers the compile check.

## Why it matters

- A false-positive gate failure turned into 120 findings, each needing a disposition before merge could continue.
- The root cause is structural (lifecycle phase vs. test-jar dependency), so it will happen again on every plan that runs this gate in this repo.

## Candidate rule (for orchestrator classification)

For a Maven reactor where modules consume a sibling's test-jar (`type=test-jar` / `classifier=tests`), do not run a stand-alone `test-compile` (or any phase before `package`) as a pre-push gate. Either rely on the full `verify` canonical command, or run the gate with `package -DskipTests` / `-am` so the test-jar exists. Related: a single structural build failure should be filed as one finding (or a cluster), not one finding per compile error, so that it does not block merge with a large rejection batch.

Possible owners: the pre-push gate / build-maven canonical-command resolution (plan-marshall), or a project-level hint for Token-Sheriff that test-compile is not a valid stand-alone gate. Classification is left to the orchestrator.
