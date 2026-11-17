envelope_version=1
sender_type=plan
sender_id=plan-09-public-helper-residuals
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-17T16:32:29Z

# Candidate lesson: scope-creep guard re-files on every loop-back as fix-task output accumulates

**Signal source:** Q-Gate findings, 5-execute, `scope_creep_warning` (component plan-marshall:phase-5-execute:scope_creep_check). All resolved in-run, `accepted`.

**Records:**
- 89b9b4 (12:21:00Z): 7 files outside declared scope (threshold 5). Accepted as the intended output of code-review fix tasks TASK-12/TASK-13 and the operator-approved request-path backstop. The phase-5 exit returned `triage_required` and deferred 5-execute -> 6-finalize until it was resolved.
- 6ac897 (12:53:04Z): 8 files, the same set plus `quarkus-config-doc.adoc`. Accepted as "Same scope-creep warning as 89b9b4".
- 9078e1 (14:33:54Z): 10 files, adding the TASK-17/18 outputs. Accepted as "Same scope-creep warning as 89b9b4 and 6ac897".

**Observation:** Each finalize loop-back (code-review, ci-verify Sonar, CodeRabbit triage) adds fix tasks whose files are not in the outline's declared scope. The guard measures each new residual set from scratch, so it raises the warning again whenever the set grows, even though most of the files were already accepted. Each re-filing needs another triage pass and another acceptance.

**Possible corrective action (for the orchestrator to judge):** Have the scope-creep check count files written by loop-back fix tasks (those with a finding or fix-task provenance) as in scope. Or carry an accepted residual set forward and warn only on the files added since the last acceptance.
