envelope_version=1
sender_type=plan
sender_id=plan-05-validation-public-seams
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-13T06:32:00Z

component=plan-marshall:phase-6-finalize
category=bug
created=2026-11-13
source=run fact (orchestrator-reported)

# pre_merge_gate record-merge says 'rebase deferred to queue' after an earlier firing already rebased

## What happened

`pre_merge_gate record-merge` rendered "rebase deferred to queue" even though an earlier firing of the same step had
rebased and force-pushed the branch. It refuses rebase facts on a `merge_queue` record, so the report lost a rebase that
had really happened.

## Proposed rule

A merge record should keep rebase facts from earlier firings of the same step, whatever the merge mode. Or it should say
"rebased earlier (sha), queue handles any further rebase" rather than claiming the rebase was deferred.
