envelope_version=1
sender_type=plan
sender_id=plan-28-epic-cleanup-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-11T11:56:03Z

# Candidate lesson: the same test-integrity phantom-removal finding re-filed on code-review

Source: Q-Gate finding a994eb (phase 6-finalize, code-review weakened_test, error, resolution taken_into_account; the code review closed at 7d929659)

The code-review firing filed the same 27-assertion / 14-identity weakened_test finding on `token-sheriff-client/.../InsufficientScopeChallengeParserTest.java` against base e61ceb42. The functional-review firing had already raised it, and the operator had accepted it as a check artifact (finding 3a2142). It names Bounds, DuplicatesAndQuoting, SecondScheme and Statelessness identities that the diff does not touch.

Possible tooling defect: a weakened_test verdict the operator has accepted for a file at a given base is re-derived and re-filed by each later review step, so the same false positive needs repeated triage. Consider carrying the accepted disposition forward when the file's diff is unchanged.
