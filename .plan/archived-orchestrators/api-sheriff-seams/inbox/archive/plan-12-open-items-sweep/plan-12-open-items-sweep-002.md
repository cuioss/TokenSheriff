envelope_version=1
sender_type=plan
sender_id=plan-12-open-items-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-19T06:28:19Z

# Candidate lesson: progress records stated dispositions that the code at HEAD contradicted or never recorded

- Signal: Q-Gate (6-finalize), rule `functional-review`, findings 880409 and 627266 (both fixed in run via TASK-12 and TASK-13)

## What happened

- 880409 (unevidenced_criterion D3.C4): the code and tests for all five PR #921 review items held, but none of the five
  dispositions was recorded in the #921 thread or the progress record. TASK-12 posted the table (comment 6987835460).
- 627266 (false_self_report): the TASK-10 progress record for PR 907 declared the CachingDiscoveryResolverAdversarialTest
  157-158 stampede nitpick a "known gap", while the test at HEAD already waited for joined callers (fixed earlier by
  dcbdcfb6, PR 912). TASK-13 posted a correction (comment 6987862615). The TASK-6 record (decision b03cbb) had already
  noted the fix at HEAD, so the run contradicted its own earlier reading.

## Evidence

- Work log 243d52 (the wrong PR 907 record), 49a74a (correction), 4acb03..14b453 (PR 921 records), 478fab (PR 906 intro
  line mislabelling f8dfa9a9 as main HEAD; advisory false_self_report 748f90).
- Decision log d1f13d, c52d24, 748f90.

## Observation for the epic

When a task dispositions review items on older PRs, each "declined / known gap" claim needs a read of the cited lines at
HEAD before it is posted; three of the posted records in this run needed a public correction.
