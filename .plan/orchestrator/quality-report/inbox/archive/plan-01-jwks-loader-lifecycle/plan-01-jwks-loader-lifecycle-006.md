envelope_version=1
sender_type=plan
sender_id=plan-01-jwks-loader-lifecycle
epic=quality-report
kind=candidate-lesson
created=2026-10-28T20:40:42Z

# Argparse rejection on `architecture --plan-id P search --content` (twice), with no recorded cause

**Source plan:** plan-01-jwks-loader-lifecycle
**Signal:** script-failure cluster (notation plan-marshall:manage-architecture:architecture), 2 occurrences
**Suggested component:** plan-marshall:manage-architecture / plan-marshall:tools-script-executor (failure-marker detail)

## What happened

Two calls exited with code 2 and `failure_kind=argparse_rejection`. Both happened during the finalize review rounds:

- 18:31:10Z: `architecture.py --plan-id plan-01-jwks-loader-lifecycle search --content --pattern HttpWellKnownResolver`
- 20:15:08Z: `architecture.py --plan-id plan-01-jwks-loader-lifecycle search --content --literal --pattern LoaderStatus.OK`

## Observation (cause not confirmed)

Unlike the other two clusters, the `detail` field of the `script_failure` marker holds only the argv, not the argparse error text. The logged argv looks valid against today's `--help`: `--plan-id` is a router-level flag placed before the verb, and `search` accepts `--content`, `--pattern` and `--literal`. So the exact reason for the rejection cannot be read from the record. One possibility is that the logged argv is a normalized version of what was actually sent, for example with `--plan-id` moved by the wrapper or the logger.

## Candidate actions

1. Investigate whether the executor's failure marker for this notation drops the argparse stderr and logs a rewritten argv. If it does, make it record the raw argv and the error text, like the `ci` and `manage-findings` markers do.
2. Until then, a dispatched reviewer agent doing a content sweep should call `architecture search --content --pattern P` without `--plan-id`, from the pinned cwd. `--plan-id` is optional on the router, and `--project-dir` excludes it.
