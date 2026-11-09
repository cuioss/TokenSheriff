envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:27:05Z

component=plan-marshall:phase-5-execute
category=improvement
created=2026-11-09
source_record=qgate 5-execute 2a9049 (scope_creep_check, taken_into_account)

# A task's log-guard fix reached nine files outside its deliverable's declared scope

## Observation

Q-Gate finding 2a9049: 9 files outside the declared scope (threshold 5) while executing deliverable 6 (log-guard work in HttpWellKnownResolver, RefreshTokenValidationPipeline, SignatureTemplateManager, TokenHeaderValidator, TokenSignatureValidator, SignatureAlgorithmPreferences and three tests). The operator authorised a scope extension; the files were added to D6 and references were re-synced.

## Candidate rule

A deliverable that sweeps a code pattern (log guards) should list its files from a content search done at outline time, not from a hand-picked set. The search result is the scope.
