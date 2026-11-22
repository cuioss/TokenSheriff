envelope_version=1
sender_type=plan
sender_id=plan-13-final-finalize
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-19T20:38:26Z

Candidate lesson from plan-13-final-finalize (Q-Gate 4-plan, finding 71fab2, scope_criterion_validator, resolution fixed).

Finding: under_coverage for deliverable 4. The criterion "No document or Javadoc this plan touches carries history or compatibility framing", searched with the deliverable's own phrases ("keeps holding unchanged", "source compatibility"), hit 9 and 8 files. Affected files listed only PushedAuthorizationEndpointMissingException.java; the same framing sat in 8 sibling exception classes.

Fix: the operator widened TASK-8; the 8 sibling exception classes became steps 4-11.

Observation: the deliverable named one file although the phrase it removes recurs across its siblings; the validator's content search found them at 4-plan.
