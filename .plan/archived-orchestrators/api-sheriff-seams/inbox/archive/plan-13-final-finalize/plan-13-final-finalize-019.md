envelope_version=1
sender_type=plan
sender_id=plan-13-final-finalize
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-19T20:38:54Z

Candidate lesson from plan-13-final-finalize (script failure, notation plan-marshall:manage-architecture:architecture).

Work log, two argparse rejections (exit 2):
- 4e413b (3-outline): `--module` is not declared for `architecture search`; it is declared on sibling verbs (`commands`, `derived-module`).
- cb7b3b (6-finalize functional-review re-fire): `architecture.py --plan-id plan-13-final-finalize search --content --ignore-case --pattern '...'` was rejected. The log line does not name the rejected token.

Observation: content search cannot be narrowed to a module with `--module`.
