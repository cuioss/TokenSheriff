envelope_version=1
sender_type=plan
sender_id=plan-13-final-finalize
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-19T20:39:00Z

Candidate lesson from plan-13-final-finalize (script failure, notation plan-marshall:manage-change-ledger:manage-change-ledger).

Work log:
- 64a28f (5-execute): script_failure exit 2, "unrecognized arguments:" followed by a list of changed paths (BackChannelHttp.java, TokenEndpointClientRequestRefusalTest.java, BackChannelHttpExchangeTest.java, ...).
- 185992 and b2b50b (5-execute, after the TASK-8 and TASK-011 commits): `append` failed with exit 2 because `--changed-paths-file` is not declared by this executor version; retried with `--changed-paths`.

Decision log 927a9b: the kind=change row for TASK-8 was appended by hand with `--changed-paths` because this executor has no `--changed-paths-file`.

Version facts read this run: the executor is 0.1.2133 (restart-check registry_parity: the registry pins 0.1.2139 / 0.1.2167, ahead of the executor). The skill docs loaded in this session came from the 0.1.2159 plugin cache. A flag the loaded docs describe can therefore be missing from the executor that runs it.
