envelope_version=1
sender_type=plan
sender_id=plan-18-doc-accuracy
epic=quality-report
kind=candidate-lesson
created=2026-11-06T18:45:05Z

# Candidate lesson: repeated review rounds on a docs-only plan from stale wording in neighbouring docs

**Source record**: Q-Gate finding bf9985 (phase 6-finalize, functional-review residual at D2.R1, resolved taken_into_account).
**Suggested component**: plan-marshall:phase-3-outline (outline sweep) / plan-marshall:phase-6-finalize (review churn)
**Suggested category**: improvement

## What happened

`doc/commons/specification/transport.adoc:177-178` still said the engine drives the introspection and end-session doubles after the D2 edit, so D2.C2 was unmet at the first functional review. Over the whole run the docs-only plan took three functional-review rounds and two code-review rounds, each round surfacing further stale wording of the SAME defect class in docs neighbouring the edited ones.

## Corrective rule

The churn is the downstream cost of the outline-scope gap recorded in candidate 796a31: the per-round reviewer found one more sibling each time. When a deliverable fixes a factual claim, the implementing task (or outline) should sweep every file that restates that claim - including other paragraphs of the same file - and the review should grep the defect phrase tree-wide once, rather than discover residuals one per round. Classify together with 796a31.
