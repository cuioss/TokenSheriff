envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:27:13Z

component=plan-marshall:phase-4-plan
category=improvement
created=2026-11-09
source_record=qgate 4-plan 74b289 (scope_criterion_validator, fixed)

# Link-check deliverable surveyed doc/ only, not every AsciiDoc file in the repository

## Observation

Q-Gate finding 74b289: deliverable 17's criterion "link check reports zero broken targets" over all AsciiDoc meant 103 files, but the deliverable listed only doc/ (56 files) and 13 module READMEs. About 35 files were outside, including module doc trees and files other deliverables changed. Fixed: TASK-38 ran the check over every tracked .adoc (106 files, 1091 links, 0 broken), and doc/client/architecture.adoc was added and corrected.

## Candidate rule

For a repository-wide doc check, take the file set from `architecture find --pattern '*.adoc'`, not from the doc/ directory alone.
