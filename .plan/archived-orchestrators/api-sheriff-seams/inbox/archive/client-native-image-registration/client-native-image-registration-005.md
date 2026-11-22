envelope_version=1
sender_type=plan
sender_id=client-native-image-registration
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-12T17:45:51Z

# Candidate lesson: `architecture search` invoked with `--module`, a flag only sibling verbs declare

**Signal source:** script-failure cluster, notation `plan-marshall:manage-architecture:architecture` (work-log hash 4e413b, 2026-11-12T11:49:21Z, phase 2-refine).

## What happened

During 2-refine source-premise verification, an agent called `architecture search ... --module X`. `search` declares only `category, content, ignore-case, literal, pattern, plan-id, pre, pre-ref, project-dir`; `--module` is declared on sibling verbs (`commands`, `derived-module`). Result: exit 2, `failure_kind=argparse_rejection`. The phase recovered and completed (5/5 premises valid).

## Candidate rule

`architecture search` cannot be scoped by module; to narrow a content search to one module use `architecture files --module X` and then `Read`, or constrain `--pattern`. This is the "flag borrowed from a sibling verb" argparse-rejection signature.

## Context

- Plan: client-native-image-registration
- Failing notation: plan-marshall:manage-architecture:architecture (verb `search`)
- Classification deferred to the orchestrator (plan-marshall tooling, not project code; candidate for the cross-plan argparse-rejection recurrence set).
