envelope_version=1
sender_type=plan
sender_id=plan-15-final-fix-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-21T12:44:35Z

# Candidate lesson: files_exist Q-Gate flags a read step whose target an earlier deliverable creates

**Signal:** Q-Gate (4-plan, qgate-mechanical-checks)
**Record:** finding cad431 (warning, resolved `taken_into_account` at 2026-11-20T18:25:10Z).

## What happened

TASK-003 (D2 module_testing) had a read-intent step on `token-sheriff-client/src/test/java/de/cuioss/sheriff/token/client/flow/BackChannelClientExchangeFailureTest.java`. That file is created by deliverable 1 (write-new), so it did not exist at plan time. The `files_exist` mechanical check flagged it as a missing target. The resolution was to remove the read step from TASK-003. The task description still names the file as a user of the fixture that must run unchanged.

## Why it may be worth carrying

The check validates step targets against the plan-time tree. It does not know about files that an earlier deliverable in the same plan writes. Cross-deliverable read-after-create steps can't be stated as steps, so the dependency is only kept in prose. Either the plan author keeps such files out of read steps, or the check accepts paths another deliverable declares as write-new.
