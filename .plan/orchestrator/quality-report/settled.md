# Settled narrative

Entries relocated verbatim out of `epic.md` by the `cleanup` verb (2026-11-06, operator-confirmed). Each subject is closed; each is reachable from a pointer left at its origin.

## Executor drift (resolved)

- Executor drift: RESOLVED as of PLAN-21 (2026-11-02): `executor_version` 0.1.2046 equals `plugin_version`
  0.1.2046 after the operator's marshall-steward run (#848). It affected PLAN-06, 07, 12 and 20. Plans started
  while the regeneration was in flight may still show mixed versions; PLAN-08 is one of them.

## Lesson store 2026-11-02

- Lesson store, 2026-11-02: the local store holds 7 lessons. `2026-10-31-12-003`, `-006`, `-007`, `-009` and the
  six `2026-10-31-13-00x` theme lessons recorded as filed on 2026-10-31 are no longer there, and I do not know
  why (no trace in this ledger's log beyond their filing). Their drafted bodies are still in the session
  scratchpad. DROPPED 2026-11-05 on the operator's say-so ("ignore", on the premise that plan-marshall fetched them;
  the premise is unconfirmed here). Not re-filed.

## Sonar read 2026-10-30 (PLAN-01 to PLAN-05 PRs)

- Sonar, verified 2026-10-30 by a direct unresolved-issue read per PR (`sonar read_open_issues` returned
  `undecidable` / `head_revision_unavailable` for all four merged PRs; the quality gate read OK on each):
  #832 has 1 open issue, INFO `java:S1135` (the TODO marker in `TokenLifecycleManager.java`, the same one
  recorded as a PLAN-02 residual), neither fixed nor dismissed; #833, #834 and #835 have 0 open. The PLAN-02
  spec requires 0 open or every issue dismissed, so that one INFO issue is an unmet criterion, minor in
  weight. The read is a PR-scoped snapshot of the last analysis, not a re-run.

## Sonar read 2026-10-30 (PLAN-06 and PLAN-07)

- Sonar, PLAN-06 and PLAN-07, verified 2026-10-30 the same way: quality gate OK on #840 and #844, direct
  `resolved=false` query returns 0 open on both (PLAN-07's landing also reports `new_code_issue_count=0`
  confirmed; PLAN-06's carries no Sonar keys). PR-scoped snapshots, not re-runs.

## Sonar read 2026-11-01 (PLAN-12 and PLAN-20)

- Sonar, 2026-11-01: gate OK and 0 open on #846 and #847; the project-wide `java:S1135` query returns 0, so
  the one open INFO on #832 recorded above is resolved (by PLAN-20). PR-scoped snapshots, not re-runs.

## Sonar read 2026-11-02 (PLAN-21, 44 open on main)

- Sonar, 2026-11-02: gate OK and 0 open on #849. The project-wide query on the default branch returns 44 open
  issues (23 MAJOR, 13 MINOR, 5 CRITICAL, 3 INFO; 41 code smells, 3 bugs; rules `java:S8714` x11, `S5778` x7,
  `S4449` x4, `S8688`, `S8786`, `S3776`, `S2589` x3 each, others), the newest created 2026-10-10, so all predate
  this epic's plans. None belongs to a plan's own PR, but the standing "fix every Sonar finding immediately"
  policy covers them. Owned since 2026-11-04 by PLAN-23 (staged). Done when a `branch=main` query returns 0 open
  or only issues with a recorded dismissal; the orchestrator re-reads it when PLAN-23 lands.
  UPDATE 2026-11-05: PLAN-23 landed as #855. Read through the Sonar REST API at the merge analysis (`0c56583e`,
  2026-11-05T07:51:36Z): `main` has 1 open issue, `java:S8786` at `JwtTokenTamperingUtil.java:450` (key
  `AZ7xhrgAXGjoVdkwztNZ`; the possessive-quantifier rewrite did not clear it). Owned by PLAN-15 (folded into
  deliverable 1). Gate `OK`. Retire this item when a `branch=main` read returns 0 or a recorded dismissal.
