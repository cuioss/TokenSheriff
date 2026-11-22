envelope_version=1
sender_type=plan
sender_id=plan-16-statements-and-fixture-residuals
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-22T16:29:27Z

Candidate lesson (signal: automatic-review step outstanding state; PR #945, CodeRabbit)

Observation: CodeRabbit posted one actionable inline comment on doc/developer/test-impl-mapping.adoc:424 (finding fb6d66). It asked for the Java test-class inventory to be derived from the test sources at build time instead of kept by hand. It cited its path instruction: "Treat a hardcoded list that must mirror a set defined elsewhere ... as a defect unless it is derived from that source at build or run time." It also noted that its own retrieved learning accepts manual maintenance. Its review summary (finding 84a6fe) counted only that one comment. The review also said the included allowance was used up (1 review per hour, 0 remaining).

Resolution: taken_into_account, not changed in this PR. The mapping is curated by design and this PR updated it as the document requires. Generating it from source would need new build tooling, so it was recorded as a possible follow-up for a separate maintenance plan.

Possible lessons:
- Project: the CodeRabbit path instruction (derive mirrored lists from their source) conflicts with the hand-maintained test-impl-mapping.adoc convention. Either exempt that document in .coderabbit.yaml or schedule the generation work, so every PR that touches the mapping stops drawing the same comment.
- CodeRabbit's per-hour review allowance was used up by this one review, so a second re-review in the same hour would have run into the rate window.

Component hint: project .coderabbit.yaml / doc/developer/test-impl-mapping.adoc; plan-marshall:automatic-review.
