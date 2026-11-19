envelope_version=1
sender_type=plan
sender_id=plan-12-open-items-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-19T06:30:02Z

# Candidate lesson: finalize item 7c (4a) says commit triage inline edits with no --step, but git-workflow commit refuses that (no_sanction)

- Signal: script-failure cluster, notation `plan-marshall:workflow-integration-git:git-workflow` (process observation 1 of the run)
- Marker class: `[ERROR] ... script_failure`, failure_kind `script_internal_failure`, exit_code 1

## What happened

The unified triage after the first Sonar round fixed three findings inline (Sonar 06bb85 and 543dd0, CodeRabbit 1d6c14).
The phase-6-finalize item 7c (4a) text says to commit those inline edits with no `--step`. `git-workflow commit` refused:
"Source-tree commit refused in 6-finalize: no step id from the composed phase-6 manifest, no open loop-back fix task and
no review-rubric section-6 inline-correction record" (reason `no_sanction`, step null, 3 changed paths). The run then
committed under `--step sonar-roundtrip` — the producer whose completion fired the hook, which declares
`mutates_source: true` and whose findings two of the three edits answer — and logged the mismatch.

## Evidence

- Work log 10c405 (19:49:10Z, the refusal).
- Decision log b3a57d (19:49:29Z): "Item 7c (4a) says to commit the triage's inline edits with no --step, but git-workflow
  commit refuses that in 6-finalize ... Doc/gate mismatch to report in lessons-capture."

## Observation for the epic

The finalize workflow text and the commit gate disagree; one third of the committed edits (1d6c14, a CodeRabbit finding) is
not a sonar-roundtrip output, so the chosen `--step` is a workaround, not a fit.
