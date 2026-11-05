envelope_version=1
sender_type=plan
sender_id=plan-10-dpop-binding-stepup
epic=quality-report
kind=candidate-lesson
created=2026-11-04T20:53:06Z

# architecture search rejected --plan-id placed before the verb (argparse rejection during functional review)

**Source**: work log 52fa9f (2026-11-04T17:17:19Z, ERROR): `script_failure notation=plan-marshall:manage-architecture:architecture exit_code=2 failure_kind=argparse_rejection detail=architecture.py --plan-id plan-10-dpop-binding-stepup search --content --pattern 'requestToken\('`.

## What happened

A functional-review dispatch called `architecture` with `--plan-id` as a router-level flag before the `search` verb. The `architecture` script does not accept `--plan-id` in that position, so argparse rejected the call (exit 2) and the content search did not run on that attempt. This is the "router-scoped --plan-id placed on the wrong side of the verb" recurrence class: the execution-context contract tells leaves to forward `--plan-id` only where the specific script declares it, but the leaf appended it by rote.

## Candidate rule

Before forwarding `--plan-id` to an `architecture` call, check the script's canonical-invocation block; `architecture search --content --pattern P` takes no pre-verb `--plan-id`. This is a single occurrence in this run (one distinct failing notation), so it is mainly useful as a recurrence data point for an existing lesson on this class.
