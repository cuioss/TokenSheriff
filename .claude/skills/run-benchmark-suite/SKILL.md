---
name: run-benchmark-suite
description: Run full benchmark suite with ablation sweep, connection sweep, JFR profiling, and doc updates
user-invocable: true
---

# Benchmark Suite Runner

Repeatable benchmark execution workflow for all 6 endpoints: health, JWT, mock-jwt, direct-validation, ablation-baseline, ablation-header-only.

**When to activate**: When the user wants to run benchmarks, collect clean data, or update analysis docs.

## Parameters

- `phase`: Which workflow to run. One of `sweep`, `connections`, `jfr`, `docs`, `all` (default: `all`)
- For `connections`: optional comma-separated list of connection counts (default: `50,100,150,200,250,300`)

## Working Directory

All benchmark commands run from `benchmarking/benchmark-integration-wrk/` relative to the project root.

## Workflow 1: Full Ablation Sweep (`phase=sweep`)

1. Navigate to `benchmarking/benchmark-integration-wrk/`
2. Run the full benchmark suite:
   ```bash
   ../../mvnw clean verify -Pbenchmark
   ```
   This runs all 6 endpoints at default connections (50) with fresh tokens per run.
3. Extract results:
   ```bash
   grep "Requests/sec" target/benchmark-results/wrk/*.txt
   ```
4. Analyze server logs — load `references/log-analysis-checklist.md` and follow the checklist against:
   - `target/quarkus.log`
   - `target/benchmark-results/keycloak-logs-*.txt`
   - `target/benchmark-results/wrk/*.txt` (for non-2xx errors)
5. Report results summary to the user.

## Workflow 2: Connection Sweep (`phase=connections`)

1. Navigate to `benchmarking/benchmark-integration-wrk/`
2. Start containers:
   ```bash
   bash ../../oauth-sheriff-quarkus-parent/oauth-sheriff-quarkus-integration-tests/scripts/start-integration-container.sh
   ```
3. Loop over connection counts (default: 50, 100, 150, 200, 250, 300):
   ```bash
   ../../mvnw verify -Pbenchmark -Dwrk.connections=$CONNS -Dskip.container.lifecycle=true
   cp -r target/benchmark-results/wrk target/benchmark-results/wrk-${CONNS}c
   ```
4. Stop containers:
   ```bash
   bash ../../oauth-sheriff-quarkus-parent/oauth-sheriff-quarkus-integration-tests/scripts/stop-integration-container.sh
   ```
5. Analyze logs for each connection count using `references/log-analysis-checklist.md`.
6. Build comparison table from results across all connection counts.
7. Report the comparison table to the user.

## Workflow 3: JFR Profiling (`phase=jfr`)

1. Navigate to `benchmarking/benchmark-integration-wrk/`
2. Run JFR profiling for JWT (default):
   ```bash
   ../../mvnw clean verify -Pbenchmark-jfr
   ```
3. Run JFR for other benchmarks (reuse containers). Supported values for `-Djfr.benchmark=`: `jwt` (default), `health`, `direct-validation`, `mock-jwt`, `ablation-baseline`, `ablation-header-only`:
   ```bash
   ../../mvnw verify -Pbenchmark-jfr -Djfr.benchmark=direct-validation -Dskip.container.lifecycle=true
   ../../mvnw verify -Pbenchmark-jfr -Djfr.benchmark=mock-jwt -Dskip.container.lifecycle=true
   ```
5. JFR output is at `target/jfr-output/`. Analyze with:
   ```bash
   jfr print --events ExecutionSample,ObjectAllocationSample,GarbageCollection target/jfr-output/*.jfr
   ```
6. Summarize hot methods, allocation sites, and GC activity.

## Workflow 4: Update Docs (`phase=docs`)

1. Read current benchmark results from `target/benchmark-results/`.
2. Update these analysis documents with fresh data:
   - `benchmarking/doc/Analysis-03.2026-Integration.adoc` — connection sweep tables (throughput, avg latency, P99)
   - `benchmarking/doc/Analysis-03.2026-Latency-Decomposition.adoc` — ablation decomposition table + six-layer decomposition
   - `benchmarking/doc/Analysis-03.2026-JFR-Profiling.adoc` — JFR analysis (only if JFR data available)
3. Preserve existing document structure; only update data tables and numbers.

## Workflow 5: All (`phase=all`, default)

Execute workflows 1 through 4 sequentially. Stop and report if any workflow fails.

## Key Project Files

| File | Purpose |
|------|---------|
| `benchmarking/benchmark-integration-wrk/pom.xml` | Maven profiles: benchmark, benchmark-jfr, quick, stress, max, autoscale |
| `benchmarking/doc/Analysis-03.2026-Integration.adoc` | Connection sweep data tables |
| `benchmarking/doc/Analysis-03.2026-Latency-Decomposition.adoc` | Ablation decomposition |
| `benchmarking/doc/Analysis-03.2026-JFR-Profiling.adoc` | JFR analysis |
| `oauth-sheriff-quarkus-parent/oauth-sheriff-quarkus-integration-tests/scripts/start-integration-container.sh` | Container lifecycle start (supports COMPOSE_OVERRIDE) |
| `oauth-sheriff-quarkus-parent/oauth-sheriff-quarkus-integration-tests/scripts/stop-integration-container.sh` | Container lifecycle stop (supports COMPOSE_OVERRIDE) |
