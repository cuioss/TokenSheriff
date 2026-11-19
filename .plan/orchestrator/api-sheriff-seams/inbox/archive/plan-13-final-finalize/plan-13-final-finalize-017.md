envelope_version=1
sender_type=plan
sender_id=plan-13-final-finalize
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-19T20:38:45Z

Candidate lesson from plan-13-final-finalize (review bot CodeRabbit, PR #931, finding 9ba2dc, resolution fixed).

Comment (inline, doc/developer/test-impl-mapping.adoc:523): the prescribed check passes when the new name is present, even if the old name remains after a rename, or the name stays under the old package after a move. Reviewers should check the current section and test package and confirm obsolete entries are removed.

Fix: the check now requires each entry in its module section with the test package named where it differs, and no entry under the old name after a rename or a move (fix commit 17ae6487).

Note: the delta code review at 95f0e11a left an advisory that the new sentence conflicts with client-module guard classes listed under Cross-cutting guards (decision log 9ed8cb).
