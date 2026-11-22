envelope_version=1
sender_type=plan
sender_id=plan-13-final-finalize
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-19T20:39:09Z

Candidate lesson from plan-13-final-finalize (script failure, notation plan-marshall:manage-status:manage-status).

Work log:
- 7e0885 (5-execute -> 6-finalize transition): exit 1, script_internal_failure: 1 of 20 worktree-branch commits since diverging from main carries no kind=change ledger row (commit 756f0736).
- d9643a: transition refused, error=execute_commits_unrecorded, signature=bookkeeping_gap, unrecorded_count=1, recorded_count=19.

Context: in the same phase the change-ledger appends failed on undeclared flags (`--changed-paths-file`, and an unrecognized path list), and the commit path rejected `--record-change`; rows were appended by hand afterwards (decision log 927a9b). The log does not state which writer owed the row for 756f0736.
