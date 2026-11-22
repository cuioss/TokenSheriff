envelope_version=1
sender_type=plan
sender_id=plan-03-client-key-material-config-validators
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-17T14:57:03Z

component=plan-marshall:phase-5-execute
category=improvement
status=active

# The build formatter rewrites imports after the quality gate, leaving a diff and a gated head that no longer matches

A formatter pass inside the whole-tree build changed a test file after the gate had stamped the head, which cost an extra commit and a ledger row.

## What happened

In plan plan-03-client-key-material-config-validators the `-Ppre-commit` build rewrote three explicit `Assertions` static imports in `DpopProofGeneratorJktGoldenTest` into the repository's wildcard form. The tree was dirty after the build, so the change was committed separately (f0f7cf41, ledger row with deliverable-id 0) and the gate was re-run. Recent commits on main (b3a08dfe, 3bc0e33b) are also chores applying `rewrite:run` output to tests, so this may be a recurring pattern rather than a one-off.

Two smaller frictions from the same run: `manage-tasks` cannot edit a task's verification commands, so the `test-compile` and `test` commands stayed on tasks that also carry a final `verify -Ppre-commit` gate; and the e2e CI job hung after 30 passing tests and needed `gh run rerun --failed`.

## Suggestion

Run the formatter goal before the first whole-tree gate, or have the gate report a formatter-only diff as its own outcome instead of a dirty tree. Allow `manage-tasks` to replace verification commands on an open task.
