envelope_version=1
sender_type=plan
sender_id=plan-13-final-finalize
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-19T20:39:04Z

Candidate lesson from plan-13-final-finalize (script failure, notation plan-marshall:workflow-integration-git:git-workflow).

Work log 193f85 (5-execute): exit 2, argparse rejection: `--record-change` is not declared for `git-workflow commit`. Declared flags: base, branch, cached, changed-paths, detach, force, force-with-lease, head, message, message-file, phase, plan-id, project-dir, push, root, scope, step, subject, type.

Context: the executor is 0.1.2133 (restart-check registry_parity), behind the registry pins 0.1.2139 / 0.1.2167. The skill docs loaded in this session came from the 0.1.2159 cache. Same version-skew cause as the manage-change-ledger `--changed-paths-file` rejections.
