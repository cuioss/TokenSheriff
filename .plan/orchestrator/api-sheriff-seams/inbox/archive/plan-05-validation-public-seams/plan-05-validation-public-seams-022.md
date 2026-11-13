envelope_version=1
sender_type=plan
sender_id=plan-05-validation-public-seams
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-13T06:32:00Z

component=plan-marshall:phase-6-finalize
category=improvement
created=2026-11-13
source=run fact (orchestrator-reported); related script-failure notation plan-marshall:phase-6-finalize:verdict_currency

# Verdict-currency re-fires full reviews on every HEAD advance, including an unrelated upstream rebase

## What happened

The verdict-currency classifier treats any HEAD advance as stale because the review steps declare no `verdict_inputs`.
A branch-cleanup rebase onto an unrelated upstream commit changed no plan file, yet it re-fired functional-review and
code-review. That cost two full review rounds plus the gate, CI and triage that follow them.

## Proposed rule

Review steps should declare their `verdict_inputs` (the footprint paths and the diff against the merge base). The
classifier should keep a verdict current when a HEAD advance leaves the plan's own diff unchanged, as a pure rebase onto
unrelated upstream commits does.
