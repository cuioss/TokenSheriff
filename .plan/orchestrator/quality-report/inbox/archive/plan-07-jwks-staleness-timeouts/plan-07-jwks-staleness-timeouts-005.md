envelope_version=1
sender_type=plan
sender_id=plan-07-jwks-staleness-timeouts
epic=quality-report
kind=candidate-lesson
created=2026-10-30T16:56:17Z

# Candidate lesson: agents still invent verbs and misplace flags on manage-* scripts

**Source signal**: script-failure clusters (argparse rejections that are agent errors rather than executor/doc drift).

**Component**: plan-marshall persona rule "Never invent script subcommands" - `plan-marshall:manage-architecture:architecture`, `plan-marshall:manage-findings:manage-findings`, `plan-marshall:manage-config:manage-config`.

## What happened

Distinct notations rejected by argparse during this plan, each because the caller guessed the surface:

- `architecture --plan-id ... search --content --pattern ...` rejected 3 times (12:27:22Z, 12:27:28Z, 13:25:17Z; security-audit and functional-review leaves) - `--plan-id` placed as a router flag before a verb that does not accept it there.
- `manage-findings qgate query` rejected at 07:49:10Z (phase-3-outline) - invented verb; registered verbs are add/clear/list/resolve/resolve-by-rule/resolve-evidenced.
- `manage-findings pr-comment ...` rejected at 16:54:05Z - invented top-level verb (pr-comments are `list --type pr-comment`).
- `manage-config effort resolve-target --step ...` rejected at 16:54:05Z - undeclared flag.
- `manage-findings qgate list` without the required `--phase` rejected at 16:54:31Z (this lessons-capture leaf made the same mistake).

The same `architecture` rejection repeated within 6 seconds, so the leaf retried the identical invalid call instead of reading `--help` once.

## Candidate rule

On an `argparse_rejection`, the caller must read the script's `--help` (or the skill's Canonical invocations block) before any retry; an identical retry of a rejected invocation should itself be flagged. The recurrence signatures here (verb paraphrase, misplaced `--plan-id`, missing required `--phase`) are the documented ones, so the doc rule exists and is not being applied - an enforcement gap, not a documentation gap.

## Evidence pointers

- Work log ERROR hashes b8575f (x2), c37b73, c3533b, 9f5646, 9dab9f, 771256.
