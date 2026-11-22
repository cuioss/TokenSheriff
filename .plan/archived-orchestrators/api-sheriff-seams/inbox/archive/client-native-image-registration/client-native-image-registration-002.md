envelope_version=1
sender_type=plan
sender_id=client-native-image-registration
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-12T17:45:24Z

# Candidate lesson: a new jar shipping native-image metadata must be reflected in the consumer-facing native-image guide

**Signal source:** Q-Gate finding 484a27 (3-outline, scope_criterion_validator, under_coverage, warning) - resolved taken_into_account in-run.

## What happened

Deliverable 1 made the `token-sheriff-client` jar ship its own `META-INF/native-image` metadata (DSL-JSON converters, served types, run-time initialization of FlowContext/PkceChallenge). The consumer-facing guide `token-sheriff-quarkus-parent/doc/configuration/native-image-configuration.adoc` (section Reflection Configuration) only described the `token-sheriff-validation` jar's shipped metadata and would have stayed silent on the client jar. The Q-Gate caught it; deliverable 3 gained a paragraph on the client jar metadata pointing to CLIENT-26.

## Candidate rule

When a change adds or alters shipped `META-INF/native-image` metadata in any module, the outline must include the consumer-facing native-image configuration guide as an affected file (or record why the guide is deliberately scoped to another module).

## Context

- Plan: client-native-image-registration (PR #898, merged c23e2a85)
- Component touched: token-sheriff-quarkus-parent/doc/configuration/native-image-configuration.adoc
- Same root shape as the sibling candidate on the requirement range: outline coverage missed a downstream document that restates the changed fact. Classification deferred to the orchestrator.
