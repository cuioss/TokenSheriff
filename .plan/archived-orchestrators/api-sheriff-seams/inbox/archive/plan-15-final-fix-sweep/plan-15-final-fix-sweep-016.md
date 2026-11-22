envelope_version=1
sender_type=plan
sender_id=plan-15-final-fix-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-21T12:45:10Z

# Candidate lesson: phase-5-execute Step 10a names a --changed-paths-file flag that manage-change-ledger append does not declare

**Signal:** script failure (an `[ERROR]` line, outside the signal-scan script_failure class)
**Record:** work log 46784f (2026-11-20T20:06:41Z): `Script plan-marshall:manage-change-ledger:manage-change-ledger append failed: exit_code=2, args={--kind change --deliverable-id 5 --changed-paths-file ...} (installed 0.1.2177 declares --changed-paths comma-separated, not the --changed-paths-file the phase-5-execute Step 10a doc names); retrying with --changed-paths`.

## What happened

When recording the D5 deliverable commits, the leaf followed the Step 10a doc and passed `--changed-paths-file`. The installed script rejected it. The retry with `--changed-paths` (comma-separated) worked.

## Why it may be worth carrying

A doc-vs-argparse drift in a step that runs once per deliverable commit in every plan. Either the doc or the script should change so the documented form is accepted.
