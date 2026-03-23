# Server Log Analysis Checklist

Run this checklist after each benchmark run to validate results.

## 1. Application Errors

```bash
grep -i "ERROR\|SEVERE\|FATAL" target/quarkus.log
```

- Any errors during the benchmark window invalidate that run.
- Common false positives: startup warnings before benchmark begins — ignore these.

## 2. Token Validation Issues

```bash
grep -i "expired\|invalid.*token\|unauthorized\|401" target/quarkus.log
```

- Expired tokens indicate the benchmark ran too long or token TTL is too short.
- Action: re-run with fresh tokens.

## 3. GC Pauses

```bash
grep -i "GC\|pause\|safepoint" target/quarkus.log
```

- Long GC pauses (>50ms) can skew latency percentiles.
- If present, note in results and consider re-running with larger heap.

## 4. Keycloak Errors

```bash
grep -i "ERROR\|WARN" target/benchmark-results/keycloak-logs-*.txt
```

- Keycloak errors during token issuance can cause inconsistent benchmark results.
- Common issue: rate limiting or connection pool exhaustion.

## 5. WRK Non-2xx Responses

```bash
grep "Non-2xx" target/benchmark-results/wrk/*.txt
```

- Any non-2xx responses mean some requests failed.
- Small numbers (<0.1%) may be acceptable; document them.
- Large numbers invalidate the run — investigate and re-run.

## 6. Container Health

```bash
grep -i "unhealthy\|restart\|OOM\|killed" target/benchmark-results/keycloak-logs-*.txt
```

- Container restarts during benchmark invalidate results completely.

## Decision

- **Clean run**: No errors in checks 1-6 -> results are valid.
- **Minor issues**: Small number of non-2xx or brief GC pauses -> note in results, usable with caveats.
- **Invalid run**: Errors in checks 1, 2, 4, or 6 -> re-run that benchmark.
