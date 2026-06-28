# JWT Validation Metrics

The token-sheriff-validation-quarkus extension provides automatic metrics collection for JWT validation operations through Micrometer integration.

## Available Metrics

| Metric Name | Type | Description | Tags |
|-------------|------|-------------|------|
| `sheriff.token.validation.errors` | Counter | Number of JWT validation errors by type | event_type, result, category |
| `sheriff.token.validation.success` | Counter | Number of successful JWT operations by type | event_type, result |
| `sheriff.token.bearer.token.validation` | Timer | Bearer token validation duration (via `@Timed`) | — |

## Metric Tags

- **event_type**: The specific type of validation error (e.g., TOKEN_EXPIRED, SIGNATURE_VALIDATION_FAILED)
- **result**: Always "failure" for error metrics
- **category**: Error category (`INVALID_STRUCTURE`, `INVALID_SIGNATURE`, `SEMANTIC_ISSUES`) when available — only present on error metrics

## Setup

1. Add the micrometer extension to your project:
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-micrometer</artifactId>
</dependency>
```

2. Optionally add a metrics registry:
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-micrometer-registry-prometheus</artifactId>
</dependency>
```

3. Metrics will be available at `/q/metrics`

## Example Queries

```promql
# Total validation errors
sum(sheriff_token_validation_errors_total)

# Error rate by category
rate(sheriff_token_validation_errors_total[5m]) by (category)

# Signature verification failures
sheriff_token_validation_errors_total{event_type="SIGNATURE_VALIDATION_FAILED"}
```

## Documentation

For complete documentation including monitoring examples, alerting configurations, and Grafana dashboard setup, see:

- **Main Documentation**: `doc/metrics-integration.adoc`
- **Grafana Dashboard**: `doc/jwt-metrics-grafana-dashboard.json`

The metrics are automatically collected when using the TokenValidator provided by the extension. No additional configuration is required beyond adding the micrometer extension.

1. Overall validation success rate
2. Error counts by category and type
3. Validation duration trends
4. JWKS cache size and health

Example Grafana dashboard JSON:

```json
{
  "annotations": {
    "list": [
      {
        "builtIn": 1,
        "datasource": "-- Grafana --",
        "enable": true,
        "hide": true,
        "iconColor": "rgba(0, 211, 255, 1)",
        "name": "Annotations & Alerts",
        "type": "dashboard"
      }
    ]
  },
  "editable": true,
  "gnetId": null,
  "graphTooltip": 0,
  "id": 1,
  "links": [],
  "panels": [
    {
      "aliasColors": {},
      "bars": false,
      "dashLength": 10,
      "dashes": false,
      "datasource": null,
      "fieldConfig": {
        "defaults": {
          "custom": {}
        },
        "overrides": []
      },
      "fill": 1,
      "fillGradient": 0,
      "gridPos": {
        "h": 8,
        "w": 12,
        "x": 0,
        "y": 0
      },
      "hiddenSeries": false,
      "id": 2,
      "legend": {
        "avg": false,
        "current": false,
        "max": false,
        "min": false,
        "show": true,
        "total": false,
        "values": false
      },
      "lines": true,
      "linewidth": 1,
      "nullPointMode": "null",
      "options": {
        "dataLinks": []
      },
      "percentage": false,
      "pointradius": 2,
      "points": false,
      "renderer": "flot",
      "seriesOverrides": [],
      "spaceLength": 10,
      "stack": false,
      "steppedLine": false,
      "targets": [
        {
          "expr": "sum(rate(sheriff_token_validation_errors_total[5m])) by (category)",
          "interval": "",
          "legendFormat": "",
          "refId": "A"
        }
      ],
      "thresholds": [],
      "timeFrom": null,
      "timeRegions": [],
      "timeShift": null,
      "title": "JWT Validation Rate",
      "tooltip": {
        "shared": true,
        "sort": 0,
        "value_type": "individual"
      },
      "type": "graph",
      "xaxis": {
        "buckets": null,
        "mode": "time",
        "name": null,
        "show": true,
        "values": []
      },
      "yaxes": [
        {
          "format": "short",
          "label": null,
          "logBase": 1,
          "max": null,
          "min": null,
          "show": true
        },
        {
          "format": "short",
          "label": null,
          "logBase": 1,
          "max": null,
          "min": null,
          "show": true
        }
      ],
      "yaxis": {
        "align": false,
        "alignLevel": null
      }
    }
  ],
  "schemaVersion": 25,
  "style": "dark",
  "tags": [],
  "templating": {
    "list": []
  },
  "time": {
    "from": "now-6h",
    "to": "now"
  },
  "timepicker": {
    "refresh_intervals": [
      "10s",
      "30s",
      "1m",
      "5m",
      "15m",
      "30m",
      "1h",
      "2h",
      "1d"
    ]
  },
  "timezone": "",
  "title": "JWT Validation Dashboard",
  "uid": "jwt-validation",
  "version": 1
}
```

## Using the Metrics in Your Application

These metrics are automatically collected when the token-sheriff-validation-quarkus module is used in your application. To use them:

1. Ensure the `quarkus-micrometer` extension is enabled in your application
2. Optionally, add a registry implementation like `quarkus-micrometer-registry-prometheus` for Prometheus integration
3. Metrics are automatically collected by `JwtMetricsCollector` when using the TokenValidator provided by the extension

The metrics will be available at the standard Micrometer/Prometheus endpoint: `/q/metrics`
