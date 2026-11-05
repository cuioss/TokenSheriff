envelope_version=1
sender_type=plan
sender_id=plan-13-log-transport-hygiene
epic=quality-report
kind=candidate-lesson
created=2026-11-05T13:19:59Z

# Standalone whole-tree test-compile stores junk build-error findings

**Defect**: A standalone whole-tree `test-compile` run in the Maven reactor cannot resolve the validation module's test-jar (it only exists after `package`). The build wrapper auto-stored 130 build-error findings for this reactor-resolution artefact, all of which then had to be bulk-resolved as `rejected` via `manage-findings resolve`.

**Corrective action**:
- Never run `test-compile` standalone on the whole tree; the whole-tree `verify -Ppre-commit` already covers test compilation.
- The build wrapper (plan-marshall:build-maven) should recognise reactor test-jar resolution failures and not store them as findings.

**Evidence**: plan-13-log-transport-hygiene, `manage-findings list --plan-id plan-13-log-transport-hygiene --type build-error --resolution rejected` (130 rows), work log.

**Suggested component**: plan-marshall:build-maven
