envelope_version=1
sender_type=plan
sender_id=client-native-image-registration
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-12T17:45:16Z

# Candidate lesson: requirement-range statements go stale when a new requirement id is added

**Signal source:** Q-Gate finding c04c7e (3-outline, scope_criterion_validator, under_coverage, warning) - resolved taken_into_account in-run.

## What happened

Deliverable 3 added requirement CLIENT-26 to `doc/client/requirements.adoc`, but the outline did not list `doc/client/README.adoc`, whose Document map states the requirement range as "CLIENT-1 to CLIENT-25" (and whose Test strategy row names the covered requirements). The range would have gone stale the moment CLIENT-26 landed. The Q-Gate caught it; the outline was amended to add the README (range -> CLIENT-1 to CLIENT-26, test-strategy row names CLIENT-26).

## Candidate rule

When a deliverable adds a new requirement id to a `requirements.adoc`, the outline must also list every document that restates the requirement id range or enumerates the requirement ids (README document maps, test-strategy tables, test-impl mappings) as an affected file.

## Context

- Plan: client-native-image-registration (PR #898, merged c23e2a85)
- Component touched: doc/client/README.adoc
- Classification deferred to the orchestrator (project-local doc convention vs. plan-marshall outline guidance).
