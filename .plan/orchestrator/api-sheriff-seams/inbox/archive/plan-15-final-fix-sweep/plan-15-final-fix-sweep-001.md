envelope_version=1
sender_type=plan
sender_id=plan-15-final-fix-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-21T12:44:21Z

# Candidate lesson: weakened_test check flags test renames as weakened tests

**Signal:** Q-Gate (6-finalize, functional-review)
**Records:** findings 05f1b7, b6c53d, 6fed4d, 7463f1, 64fd02, 423a43, 357bc8, 623b48, f7f61d, 743b0a, ec68c0, e5f910, ded11c, 3ad6f8, e06b0b, 34f554, 3f8a8b, 2d441f, 7741dc. All 19 resolved `accepted` at 2026-11-21T08:31:22Z.

## What happened

PLAN-15 deliverable 3 renamed test methods and two test classes whose names described history (for example `...Unchanged`, `...RegressionTest`) by git mv and method renames. The assertions stayed the same. The functional-review `weakened_test` check compared assertions per test method against base 08e96263. It filed one `error` finding for each renamed method or class: "N assertion(s) ... are gone from X::oldName, while the file total is not reduced by the same amount", and "... are gone from every file the branch carries" for the two class renames.

The operator accepted all 19. 17 were renames the spec required: assertions unchanged, 5708 tests ran before and after, and assertion counts were equal for the two class renames. One was a deliberate inversion of a test. One was a merge of a test into a parameterised test.

## Why it may be worth carrying

The check keys assertions by method/class name. A rename-only commit therefore reads as weakened tests and gives a burst of error-severity findings that need an operator decision. The detail text even notes the file total did not drop. A per-file total comparison or git rename detection (`-M`) would tell a pure rename apart from a removed assertion.
