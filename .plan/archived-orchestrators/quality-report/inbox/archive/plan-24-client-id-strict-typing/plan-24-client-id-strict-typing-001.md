envelope_version=1
sender_type=plan
sender_id=plan-24-client-id-strict-typing
epic=quality-report
kind=candidate-lesson
created=2026-11-06T16:55:58Z

# Candidate lesson: `architecture search` has no `--module` scope flag

**Source signal:** script_failure cluster (plan-24-client-id-strict-typing, phase 2-refine, 2026-11-06T14:06:25Z)
**Notation:** `plan-marshall:manage-architecture:architecture`
**Failure kind:** argparse_rejection (exit 2)

## What happened

During source-premise verification the refine agent called `architecture search ... --module token-sheriff-validation`
to scope a content search to one module. `search` declares only `--pattern`, `--content`, `--category`,
`--ignore-case`, `--literal`, `--pre`, `--pre-ref`, `--plan-id`, `--project-dir`. `--module` exists only on sibling verbs
(`commands`, `derived-module`, `files`), so the call was rejected before the script body ran.

## Reusable rule (suggested)

`architecture search --content` is a whole-inventory sweep; it cannot be module-scoped by flag. To narrow by module,
either run the unscoped search and filter the returned paths by module prefix, or list the module's files with
`architecture files --module X` and then `Read` the candidates. Do not carry `--module` over from sibling verbs.

## Suggested component

`plan-marshall:manage-architecture` (client-api.md § search), category anti-pattern. Matches the "flag declared on
sibling verb" argparse-rejection recurrence signature.
