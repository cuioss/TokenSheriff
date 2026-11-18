envelope_version=1
sender_type=plan
sender_id=plan-11-final-residuals-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-18T11:38:02Z

## Candidate lesson: weakened_test findings re-raised on every HEAD advance

- Component: plan-marshall:phase-6-finalize (test-integrity / merge-gate verdict validator)
- Suggested category: bug
- Source plan: plan-11-final-residuals-sweep (PR #921, merge d8aba512)

### Observation

The finalize verdict validator raised the same five `weakened_test` blocking findings again on every HEAD advance (test-file moves and base merges). An operator close of those findings could not record the test identities it was closing (the validator emitted `accepted_removals_warning`), so the close did not stick, and the run needed four separate operator overrides for the same five findings.

### Proposed rule

An operator-accepted `weakened_test` finding must be keyed on a stable test identity (class + method, or a moved-file mapping), not on the HEAD it was graded at, so that an accepted removal or move survives later HEAD advances. The close verb should accept and persist those identities instead of only warning.

### Related existing lesson

2026-11-17-06-001 (plan-marshall:phase-6-finalize) covers a different false positive in the same check (import-static rewrites counted as weakened assertions). This candidate is about persistence of the operator close across HEAD advances; consider merging into that lesson as a recurrence or keeping it separate.
