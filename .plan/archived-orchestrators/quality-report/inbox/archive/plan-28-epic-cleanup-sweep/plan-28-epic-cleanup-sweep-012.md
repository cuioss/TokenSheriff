envelope_version=1
sender_type=plan
sender_id=plan-28-epic-cleanup-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-11T11:55:47Z

# Candidate lesson: test-integrity check over-reports removed assertions around raw U+2028 (functional-review firing)

Source: Q-Gate finding 3a2142 (phase 6-finalize, functional-review weakened_test, error, resolution accepted by operator via AskUserQuestion)

test-integrity reported 27 assertions gone across 14 test identities in `InsufficientScopeChallengeParserTest`. The real diff against origin/main removes 1 assertion line and adds 9. The only genuine removal is the deliberate shouldAcceptUnquotedScope -> shouldRefuseUnquotedScope inversion. The other 13 identities are untouched by the diff. The most likely cause is the check's line-based parsing of a base file that holds raw U+2028 characters (Java treats them as ordinary characters, but line splitters treat them as line breaks).

Possible tooling defect for the framework owner: the test-integrity assertion counter should split on `\n` only (or parse the Java source), not on Unicode line separators, so a file with a raw U+2028 does not produce phantom removals.
