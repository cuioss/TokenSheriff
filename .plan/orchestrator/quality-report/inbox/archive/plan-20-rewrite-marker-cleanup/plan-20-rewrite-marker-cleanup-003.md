envelope_version=1
sender_type=plan
sender_id=plan-20-rewrite-marker-cleanup
epic=quality-report
kind=candidate-lesson
created=2026-11-01T19:34:07Z

Candidate lesson (source: script-failure clusters in the work log, 3 distinct failing notations)

Several script calls were rejected because the verbs or flags they used do not exist on the installed script surface:
- manage-findings qgate query
- manage-solution-outline get-deliverable --number
- pre_merge_gate record-merge
- review_completeness roster

Cause: the installed plugin's generated executor (0.1.2027) was older than the 0.1.2046 skill docs loaded into context. The docs described verbs and flags that the executor's scripts did not provide.

Observation: when the generated executor is older than the loaded skill docs, documented calls fail with argparse rejections. Checking whether the executor needs regenerating (marshall-steward executor staleness) before a plan run would prevent this failure class.
