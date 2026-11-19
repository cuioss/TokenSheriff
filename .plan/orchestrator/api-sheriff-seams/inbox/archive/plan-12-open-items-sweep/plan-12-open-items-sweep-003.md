envelope_version=1
sender_type=plan
sender_id=plan-12-open-items-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-19T06:28:29Z

# Candidate lesson: functional-review author graded a not-met criterion as met_with_residuals without filing a finding

- Signal: Q-Gate (6-finalize), findings dee77b (rule `functional-review`, fixed via TASK-14) and 9e2674 (rule `functional-review-state`, verdict_refused, accepted at operator close)

## What happened

In the second functional-review round the author graded deliverable 4 as `met_with_residuals` although criterion D4.C1
was not met: `doc/developer/test-impl-mapping.adoc` had no row for `BearerScopeValidationGeneratedBeanControlBootTest`,
the second top-level class declared at line 139 of the same test file. The verifier declined the verdict (9e2674) and the
gap became finding dee77b. TASK-14 then found a second unmapped second-top-level class (`DiscoveryResolverSizeCapTest`).
The same gap recurred in round 4: `BearerScopeValidationCombinedIndexBuildStepTest` (added by TASK-16) again had no
mapping row and was fixed through TASK-17.

## Evidence

- Decision log 0c46cc (verifier declined), 852ed0 (closing round accepted), ecc381 (round 4, second recurrence).
- Work log 16dd63 (TASK-7 sweep compared file-named classes only and reported 0 misses), 6566f2 (TASK-14 widened the scan
  to files declaring 2+ top-level types).

## Observation for the epic

A mapping sweep keyed on file names misses second top-level classes, and new test classes added by later fix tasks do not
re-trigger the sweep; the verifier, not the author, caught both.
