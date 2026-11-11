envelope_version=1
sender_type=plan
sender_id=plan-28-epic-cleanup-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-11T11:55:30Z

# Candidate lesson: raw invisible Unicode characters in Java test string literals

Source: Q-Gate finding 3c578d (phase 6-finalize, functional-review residual D8.R1, error, resolution taken_into_account, superseded by the functional review round at 31e39c3d)

`InsufficientScopeChallengeParserTest` held raw U+2028 characters in string literals (lines 244 and 254, shouldEscapeDescription and shouldEscapeLineSeparatorInRealm). The new Unicode-blank tests used a raw U+2003 literal. Fixed by TASK-018, which escapes each one.

Related downstream effect: the raw U+2028 in the base file is the likely cause of the test-integrity parsing artifact that reported 27 "removed" assertions (see the weakened_test candidates).

Possible rule: test literals for control, separator or blank code points must use `\uXXXX` escapes, never the raw character. Raw separators are invisible in review and break line-based tooling.
