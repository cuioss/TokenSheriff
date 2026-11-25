envelope_version=1
sender_type=plan
sender_id=plan-13-renumber-commons-validation
epic=client-framework-aspects
kind=candidate-lesson
created=2026-11-24T22:40:00Z

# Candidate lesson: do not reuse earlier green builds for the pre-push gate without a fresh coverage verdict

Source: work log, phase-6-finalize pre-push-quality-gate, 2026-11-24T17:15:14Z and 17:15:24Z.

Observation: the gate first recorded the phase-5 whole-tree builds (verify -Ppre-commit, test) as reused evidence, because HEAD 4002d4fe was identical, the tree was clean, and pre-commit-verify-freshness reported fresh. It withdrew that 10 seconds later: freshness reported `scope_reason: required_coverage_unknown` (evidence unaudited), so the arms had to run as documented anyway.

Corrective rule: "same HEAD + clean tree + fresh" is not enough to skip the pre-push arms. Read the freshness verdict's `scope_reason` first. Any value other than a confirmed coverage match (in particular `required_coverage_unknown`) means the gate runs. Decide once, after reading the full verdict, so no decision has to be reversed in the log.

Transferability: any finalize run in the epic where phase-5 already ran whole-tree builds.
