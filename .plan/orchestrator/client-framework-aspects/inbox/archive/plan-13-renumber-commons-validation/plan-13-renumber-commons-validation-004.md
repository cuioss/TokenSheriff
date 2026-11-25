envelope_version=1
sender_type=plan
sender_id=plan-13-renumber-commons-validation
epic=client-framework-aspects
kind=candidate-lesson
created=2026-11-24T22:39:49Z

# Candidate lesson: script flags and verbs drift between the installed plugin and its docs (argparse rejections)

Source: script-failure cluster `plan-marshall:manage-findings:manage-findings` plus related work-log errors.

Observations (installed plugin cache 0.1.2184/0.1.2185):
- 2026-11-24T12:27:18Z phase-3-outline: `manage-findings qgate query` -> exit 2, argparse_rejection. `query` is not a registered verb; valid verbs are add, clear, list, resolve, resolve-by-rule, resolve-evidenced. This is an invented verb, the verb-paraphrase recurrence signature.
- 2026-11-24T14:40:56Z and 20:32:39Z phase-5-execute: `manage-change-ledger append --changed-paths-file` -> exit 2 (flag undeclared in the installed executor; `--changed-paths` works). The same flag shape failed twice in one run.
- The quality gate auto-rewrote RequirementIdIntegrityTest twice. Those changes had to go through a git-workflow commit with a manually appended ledger row because this plugin version has no `--record-change` flag.
- `platform_runtime subagent dispatch --agent execution-context-level-4` returned `prompt_not_found`, because the plugin is installed from the cache, not from a marketplace tree in this repo. The run fell back to direct Agent-tool dispatch.

Corrective rule: before a first call on a surface, take verbs and flags from the installed script's `--help` (or its canonical-invocations block), not from workflow prose. When the docs name a flag that the installed version rejects, record it as doc/version drift instead of retrying variants.

Transferability: affects every plan in the epic running on the same installed plugin version. A likely global or plugin-side lesson, not a TokenSheriff one.
