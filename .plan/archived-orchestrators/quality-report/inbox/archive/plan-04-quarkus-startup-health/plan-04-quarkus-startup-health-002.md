envelope_version=1
sender_type=plan
sender_id=plan-04-quarkus-startup-health
epic=quality-report
kind=candidate-lesson
created=2026-10-29T15:40:56Z

component=build-pre-commit
category=bug
source_plan=plan-04-quarkus-startup-health
source_records=b45d41 (5-execute Q-Gate, fixed)

# Pre-commit formatter switches between star imports and explicit imports, leaving the tree dirty after verify

## What happened

`verify -Ppre-commit` rewrites static imports in test sources. In plan-04 it merged three explicit
static hamcrest `Matchers` imports in `ProblemDetailMappingIT.java` into a wildcard import. In other
commits it expanded wildcards back into explicit imports. So every verify run can leave an
uncommitted import rewrite behind. The final quality sweep reported a dirty tree
(`post_run_source_guard clean=false`, finding b45d41). The fix was a separate style commit, followed
by another sweep.

## Rule

- After every `-Ppre-commit` verify, check the tree. Commit build-produced import rewrites as a style
  commit on the deliverable before any freshness or sweep gate runs.
- Root cause to fix: align the formatter/OpenRewrite import settings (star-import threshold, static
  import handling) with the IDE/checkstyle conventions so the rewrite reaches one fixed point instead
  of flipping back and forth.

## Impact

Each flip dirties the tree, forces another commit, and restarts the verify, gate, and review cycle.
