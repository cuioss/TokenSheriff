envelope_version=1
sender_type=plan
sender_id=plan-13-log-transport-hygiene
epic=quality-report
kind=candidate-lesson
created=2026-11-05T13:20:12Z

# Dispatch usage accounting skewed across the execute/finalize boundary

**Defect**:
- Execute-phase subagent usage (about 374k + 363k tokens) was accumulated into the 6-finalize accumulator, so the per-phase metrics split is skewed.
- One zero-usage dispatch-boundary row was recorded before the real usage arrived.

**Corrective action**:
- Reset/flush the usage accumulator at the phase boundary.
- Refuse to record a dispatch-boundary row with zero usage.

**Evidence**: plan-13-log-transport-hygiene metrics (6-finalize accumulator totals, zero-usage dispatch-boundary row).

**Suggested component**: plan-marshall:manage-metrics
