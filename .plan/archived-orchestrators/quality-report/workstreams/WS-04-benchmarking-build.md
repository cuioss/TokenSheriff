# WS-04: Benchmark validity and build hygiene

epic: quality-report

> Charter document for one workstream. Tracked in the `workstreams[]` field of `status.json`.

## Charter

Make the published benchmark numbers mean what they claim (shared-validator concurrency,
non-lossy metrics aggregation), remove stale references, and fix the BOM's test-scope pin.

## Scope

- In scope: `benchmarking/**`, `bom/pom.xml`.
- Out of scope: performance docs prose (PLAN-18 owns D-13).

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-12-benchmark-build | staged | Q-7, Q-10, Q-11, Q-13 |

## Sequencing and Surface Notes

- Disjoint from every other plan; a good parallel partner for any WS-01/WS-02 plan.
