envelope_version=1
sender_type=plan
sender_id=plan-16-keycloak-it-assertions
epic=quality-report
kind=candidate-lesson
created=2026-11-06T06:03:07Z

## Candidate lesson: a draft ADR number collided with ADRs merged upstream during the run

**Source signal**: Reported by the dispatching orchestrator for plan plan-16-keycloak-it-assertions (PR #868). No separate finding record.

**What happened**: The plan drafted an ADR under a number chosen early in the run. Before it landed, other ADRs were merged to `main` (concurrent quality-report plans, e.g. #863 "residual docs, tests, ADRs"). The draft's number then collided with one of them and had to be renumbered.

**Candidate rule**: Do not fix an ADR number at draft time on a long-running plan. Allocate it (via `manage-adr` automatic numbering) as late as possible, after syncing with the base branch, or re-check it against the base branch at finalize, before the commit and the PR. In an epic with several plans landing in parallel, treat ADR numbers as a shared sequence that can be contended, like any other.

**Scope hint for the orchestrator**: manage-adr numbering, plus the finalize pre-commit/base-sync step. It matters most at epic level, where sibling plans in the same workstream add ADRs at the same time.
