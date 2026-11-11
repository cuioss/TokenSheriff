envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:27:44Z

component=plan-marshall:phase-3-outline
category=improvement
created=2026-11-09
source_record=run event (dispatcher-reported): 163-file plan footprint over the review-bot cap

# A sweep plan with 26 deliverables grew to 163 files, over the required review bot's cap

## Observation

PLAN-25 (epic leftover sweep) bundled 26 deliverables across validation, client, Quarkus, docs and e2e. The final head touched 163 files, over CodeRabbit's 100-file cap, so the PR had to be split into two parts at finalize time. Part 1 (validation, 74 files) merged as #883.

## Candidate rule

At outline time, estimate the plan's file footprint. When it can exceed a required review bot's file cap, split the work into separate plans or planned PR parts by module boundary before execution.
