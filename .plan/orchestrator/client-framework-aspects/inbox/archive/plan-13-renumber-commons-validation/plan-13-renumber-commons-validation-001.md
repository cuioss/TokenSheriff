envelope_version=1
sender_type=plan
sender_id=plan-13-renumber-commons-validation
epic=client-framework-aspects
kind=candidate-lesson
created=2026-11-24T22:39:18Z

# Candidate lesson: per-task verification must build every module the task edits

Source: Q-Gate 4-plan finding 2f6f20 (module_mapping_validator), resolved taken_into_account (operator chose "Extend verification").

Observation: a task edited files in two Maven modules (token-sheriff-validation-quarkus and token-sheriff-client-quarkus), but its verification command was `package -pl token-sheriff-validation-quarkus -am`. `-am` builds upstream dependencies only, so the sibling module's edit (ClientExceptionMapper) would have been compiled only by the final reactor verify.

Corrective rule: when a task or deliverable touches more than one module, list every touched module in `-pl` (e.g. `-pl validation-quarkus,client-quarkus -am`), or split the edit into its own task with its own module-scoped verification. `-am` does not cover sibling modules.

Transferability: applies to any multi-module Maven plan in this epic.
