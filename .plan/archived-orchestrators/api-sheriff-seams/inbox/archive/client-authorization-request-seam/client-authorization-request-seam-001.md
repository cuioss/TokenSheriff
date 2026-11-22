envelope_version=1
sender_type=plan
sender_id=client-authorization-request-seam
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-13T13:58:15Z

component=plan-marshall:build-maven
category=bug

# Reactor-root module-tests/test-compile canonical commands fail when a module consumes a sibling's test-jar classifier

## Observation

In the TokenSheriff reactor, the reactor-root canonical commands for `module-tests` and `test-compile` resolved to `test` / `test-compile`. Those goals fail in a reactor where one module consumes another module's `test-jar` classifier artifact (e.g. token-sheriff-validation `generators`). The test-jar is only attached at the `package` phase, so `test`/`test-compile` cannot resolve it in-reactor.

Effect: the pre-push gate arms went red on a correct change.

## Workaround applied this run

The installed plugin cache copy of `_maven_cmd_discover.py` was patched locally to resolve `package` (module-tests) and `package -DskipTests` (test-compile) when the reactor carries test-artifact consumption (reactor_test_artifacts metadata). The same defect exists in cached versions 2114 and 2115. The patch lives only in the user's plugin cache and is lost on the next plugin update.

## Proposed rule

Canonical-command discovery for build-maven must detect intra-reactor `test-jar` / `tests`-classifier consumption and lift `module-tests` / `test-compile` at reactor root to `package` / `package -DskipTests`. The fix must land in the marketplace source so it survives plugin updates.
