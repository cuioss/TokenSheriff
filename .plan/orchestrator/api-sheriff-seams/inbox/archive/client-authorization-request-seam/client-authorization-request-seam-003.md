envelope_version=1
sender_type=plan
sender_id=client-authorization-request-seam
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-13T13:58:24Z

component=plan-marshall:build-npm
category=bug

# Build wrapper stores npm/Jest console noise as build-error findings

## Observation

The build wrapper stored ordinary npm / Jest output noise (non-failure console lines from the frontend module built inside the Maven reactor) as `build-error` entries.

## Why it matters

False build-error entries inflate the error signal, can feed the script-failure / findings counts, and cost triage time on non-defects.

## Proposed rule

The npm/Jest output parser must only classify lines as build errors when they belong to a real failure (failing test, non-zero npm exit with error marker, lint error); warnings and informational console output from passing runs must not be filed as build-error.
