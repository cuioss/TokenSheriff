envelope_version=1
sender_type=plan
sender_id=plan-15-final-fix-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-21T12:45:09Z

# Candidate lesson: `manage-findings qgate list` invoked without the required --phase

**Signal:** script failure (manage-findings; recorded after the dispatcher's signal-scan snapshot)
**Record:** work log 771256 (2026-11-21T12:40:03Z, right before the lessons-capture dispatch at 12:40:07Z): `script_failure ... manage-findings exit_code=2 failure_kind=argparse_rejection detail=Add the required flag(s) to manage-findings qgate list: ['phase']`.

## What happened

While the lessons-capture Signal Gate was being evaluated, a `qgate list` call left out `--phase`. The verb requires `--phase` (one of 2-refine ... 6-finalize), so counting pending Q-Gate findings "across 2-refine..6-finalize" takes one call per phase. A later signal-scan in this step lists this notation as a sixth failing notation (script_failure 8), on top of the five the dispatcher forwarded.

## Why it may be worth carrying

The Signal Gate's "sum across five phases" wording suggests a single call. The gate doc could show the per-phase loop, or the verb could accept an all-phases form. This is the missing-`--phase` recurrence signature.
