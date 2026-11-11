envelope_version=1
sender_type=plan
sender_id=plan-09-dpop-parser-hardening
epic=quality-report
kind=candidate-lesson
created=2026-11-04T12:33:31Z

# Candidate lesson (recurrence): the orchestrating session made up manage-* verbs and flags instead of checking --help

**Source plan:** plan-09-dpop-parser-hardening (PR #851)
**Signal:** 2 distinct failing script notations in the work log (script-failure clusters). Both were argparse rejections (exit 2) issued by the orchestrating main-context session itself, not by a dispatched leaf.
**Suggested component:** plan-marshall:persona-plan-marshall-agent (Never invent script subcommands) / pm-plugin-development:recipe-fix-argparse-rejection

## What happened

1. **2026-11-02, `plan-marshall:manage-execution-manifest:manage-execution-manifest record-step`** was called with a flag that `record-step` does not declare. The real flags are `--plan-id --step-id --phase --outcome` plus the optional `--total-tokens`, `--tool-uses` and `--duration-ms`. The `--phase` value must be `5-execute` or `6-finalize`, and `--outcome` must be one of `executed|skipped|loop_back|failed|error`.
2. **2026-11-04, `plan-marshall:manage-findings:manage-findings`** was called with verbs that do not exist: `query` (the real verbs are `list` / `get`) and `qgate query` (the real `qgate` verbs are `add|list|resolve|resolve-evidenced|resolve-by-rule|clear`, and `qgate list` requires `--phase`).

Both match the existing recurrence signatures "verb-paraphrase" and "missing `--phase`" in `agent-behavior-rules.md` § Never invent script subcommands. Neither failure affected the outcome, because both were retried with the real form, but both show up in the run's script-failure signal.

## Reusable rule / observation

The known rule already covers dispatched leaves. This run shows it also applies to the orchestrating main-context session, which drives `manage-*` scripts ad hoc between dispatches without a loaded workflow that quotes the canonical invocation. Before an ad-hoc `manage-*` call that no loaded workflow spells out, run `{script} {verb} --help` or check that skill's "Canonical invocations" section. For findings, the read verbs are `list` / `get` and `qgate list --phase P`, never `query`.

**Classification hint for the orchestrator:** this is likely a merge into the existing argparse-rejection lesson or rule (a recurrence count bump with the new "orchestrator session" scope), not a new lesson.
