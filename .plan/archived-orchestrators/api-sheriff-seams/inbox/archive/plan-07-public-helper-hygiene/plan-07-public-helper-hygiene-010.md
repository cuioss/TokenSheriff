envelope_version=1
sender_type=plan
sender_id=plan-07-public-helper-hygiene
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-15T22:32:17Z

# Candidate lesson: the weakened-test detector re-raises the same test-class split flags at every new head

**Source signal**: orchestrator-observed during the run, checked against the plan's decision log.

## What the record shows

The plan split over-long test classes on purpose (TASK-014 logout and signature-verification tests, TASK-013 JwksEndpointHealthCheckTest, TASK-017 loader tests). The detector reported six `weakened_test` flags for these splits. The verifiers confirmed they were pure splits with no lost test method or assertion compared with origin/main (decision log 17:55:23Z "F5-F10 are pure splits"; 18:41:56Z "the five other splits lost no test method vs origin/main"). The flags were accepted, but every later review round at every new head still listed them as blocking with may_close=false:

- functional-review 18:41:56Z, 20:54:30Z, 21:05:34Z, 21:15:04Z, 21:22:51Z: "the six accepted weakened_test flags still blocking"
- code-review 18:53:56Z ("F8-F13 are detector verdicts (the accepted split-test flags)"), 21:13:09Z, 21:20:59Z: "the six accepted weakened_test splits are the only blocking findings"
- a per-head override was recorded (`code_review_override_heads += e07e4960`, work log 21:08:02Z)

## Suggested rule / gap (for orchestrator classification)

When a test class is split and no rename follows the old file, the detector reads it as a weakened test. An acceptance at one head does not carry to the next head, so every fix commit asks the operator to close the same six flags again. Possible fixes: key the acceptance on the split's content (old file to new files, same method set) rather than on the head, or have the detector recognise a split by matching method sets. Until then, a plan that splits test classes should expect a manual close at every head after review.
