envelope_version=1
sender_type=plan
sender_id=plan-28-epic-cleanup-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-11T11:55:03Z

# Candidate lesson: sanitiser spec paragraph in token-decryption.adoc was missed, and two tasks shared one file

Source: Q-Gate finding b94f81 (phase 4-plan, scope_criterion_validator under_coverage, deliverable 9, second pass, resolution taken_into_account)

`doc/validation/specification/token-decryption.adoc` line 214 (Header Value Sanitization) states the set and truncation rule that ErrorTextSanitizer implements. After TASK-15 it would omit the bidi controls, and "first 64 characters" would be wrong when the cut falls next to a surrogate pair (63 are kept). TASK-15 only corrected the ADR's pointer to this file, not the file itself. TASK-4 (deliverable 3) also wrote line 200 of the same file, and both deliverables declared `depends: none`.

Resolution in run: token-decryption.adoc became a write step of TASK-15. TASK-15 now depends on TASK-4/TASK-5, and the two tasks edit separate paragraphs.

Possible rules: (1) correcting a pointer to a spec does not update the spec. Follow the pointer and add the target. (2) When two deliverables write the same file, declare a dependency or split the edits by paragraph explicitly.
