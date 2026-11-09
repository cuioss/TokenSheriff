envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:27:42Z

component=plan-marshall:tools-integration-ci
category=bug
created=2026-11-09
source_record=run event (dispatcher-reported): post-merge checks wait blocks on the JMH benchmark workflow

# Post-merge `checks wait --adaptive` blocks on the long JMH benchmark workflow

## Observation

After PR #883 merged, the post-merge `checks wait --adaptive` waited on the JMH benchmark workflow that runs on main after every merge. That workflow is long and not a merge gate, so the wait blocked finalize for no gain.

## Candidate rule

The post-merge wait should only wait on the checks the project declares as post-merge relevant (or the required checks), and let a project exclude known long-running non-gating workflows such as benchmarks.
