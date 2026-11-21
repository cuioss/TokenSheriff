envelope_version=1
sender_type=plan
sender_id=plan-15-final-fix-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-21T12:45:08Z

# Candidate lesson: manage-files invoked with the shell-style verb `ls` instead of `list`

**Signal:** script failure cluster `plan-marshall:manage-files:manage-files`
**Record:** work log a3d40a (2026-11-21T11:50:13Z): `script_failure ... manage-files exit_code=2 failure_kind=argparse_rejection detail=ls is not a registered verb ... use manage-files list — registered: ['create-or-reference', 'detect-ide', 'discover', 'exists', 'list', 'mkdir', 'open-in-ide', 'read', 'remove', 'write']`.

## What happened

During the 4e1fef61 review re-fire, a review leaf tried to list plan work files with `manage-files ls`. The rejection named the right verb, `list`.

## Why it may be worth carrying

This is the verb-paraphrase recurrence signature: a shell habit carried over to a manage-* verb. It's a single, cheap occurrence. Worth carrying only if it recurs across plans, for example as a registered alias or a named recurrence example.
