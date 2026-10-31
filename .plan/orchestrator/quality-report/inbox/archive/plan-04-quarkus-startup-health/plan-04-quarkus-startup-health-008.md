envelope_version=1
sender_type=plan
sender_id=plan-04-quarkus-startup-health
epic=quality-report
kind=candidate-lesson
created=2026-10-29T15:41:54Z

component=plan-marshall:build-maven
category=bug
source_plan=plan-04-quarkus-startup-health
source_records=script-failure cluster "failed full verifies and bare test-compile"

# Failed full verifies and a bare test-compile run formed a script-failure cluster

## What happened

During plan-04 several full `verify` runs failed, and a bare `test-compile` invocation also failed.
Together they formed one of the three script-failure clusters counted for this run. The other two
clusters (the stale `~/.m2` native build and the `head_not_validated_by_gate` refusals) are filed as
separate candidates.

This candidate is transmitted with the detail available at lessons-capture time. The underlying
work-log `[FAILED]` and `[ERROR] ... script_failure` lines were not re-analysed here, so the
orchestrator-side pickup should read them before classifying.

## Questions for classification

- Were the failed full verifies real code failures, or build-environment failures such as stale
  upstream artifacts, a dirty tree from the pre-commit import rewrite, or a timeout? If the cause
  was stale artifacts, merge this into the stale-`~/.m2` candidate from the same plan.
- Was the bare `test-compile` run without `-am` against a module whose upstream had changed? If so,
  the rule is the same: module-scoped builds must include `-am` (or follow an `install` of the
  changed upstream modules) when upstream sources changed on the branch.

## Impact

Each failed full verify costs a full Maven reactor run. The time is wasted when the failure comes
from the environment rather than from the code.
