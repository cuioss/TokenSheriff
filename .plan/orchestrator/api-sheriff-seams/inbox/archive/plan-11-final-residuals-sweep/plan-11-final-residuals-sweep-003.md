envelope_version=1
sender_type=plan
sender_id=plan-11-final-residuals-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-18T11:38:16Z

## Candidate lesson: commit gate refuses review-triage source edits in 6-finalize

- Component: plan-marshall:phase-6-finalize (finalize commit gate / review-triage hand-back)
- Suggested category: improvement
- Source plan: plan-11-final-residuals-sweep (PR #921, merge d8aba512)

### Observation

While applying edits that review triage asked for during 6-finalize, the commit gate refused the source commit because it carried neither a sanctioned step id nor a section-6 inline-correction record. Review-triage fixes are a normal, expected source change in finalize, but the workflow did not tell the agent to create either record before committing, so the commit failed first and had to be repaired afterwards.

### Proposed rule

The review-triage fix path in 6-finalize must either supply the sanctioned step id to the commit automatically, or tell the agent, before the edit, to file the section-6 inline-correction record. A refusal message should name the exact verb that creates the missing record.
