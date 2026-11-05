envelope_version=1
sender_type=plan
sender_id=implement-plan-23-sonar-backlog-cleanup
epic=quality-report
kind=candidate-lesson
created=2026-11-05T07:53:57Z

component=plan-marshall:phase-6-finalize
category=improvement
title=Owed architecture hint: suppress java:S1612 on assertDoesNotThrow over an overloaded method (token-sheriff-validation tests)

Owed `architecture enrich` call (the preference-emitter runs after the merge gate and must not write tracked source; run this on main):

- target module: token-sheriff-validation (the two findings carry the Sonar project key cuioss_TokenSheriff as their `module` attribution; their component path resolves to this architecture module, so route it here)
- verb: best-practice
- generalized hint (verbatim):

  Prefer to suppress Sonar java:S1612 (replace lambda with method reference) in token-sheriff-validation tests when the suggested method reference is ambiguous between assertDoesNotThrow(Executable) and assertDoesNotThrow(ThrowingSupplier) because the target method is overloaded (for example CountDownLatch.await): the suggestion does not compile, so the lambda stays and the rule is suppressed with a stated reason rather than the code being rewritten.

Pattern evidence: the suppressed disposition recurred twice for the same finding class in plan implement-plan-23-sonar-backlog-cleanup (threshold 2). Routing note: the finding module attribution was the Sonar project key, not an architecture module; confirm the target module at pickup.
