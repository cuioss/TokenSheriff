envelope_version=1
sender_type=plan
sender_id=plan-15-validation-test-gaps
epic=quality-report
kind=candidate-lesson
created=2026-11-06T08:14:59Z

# Candidate lesson: a CWE-117 fix comment claimed sanitization while the raw exception still went to LOGGER.error

## Source records

- Q-Gate c3ae0b — phase 6-finalize, code-review, DiscoveryResolver.java:230, severity error, resolution fixed (commit 89632332; DiscoveryResolverTest passes, 6 tests).

## What happened

The TASK-8 fix for the truncated-escape leak in DiscoveryResolver added a comment stating the log line was CWE-117 safe, but the code still passed the raw exception object to LOGGER.error. The exception message carries attacker-controlled JSON content, so the log-injection protection the comment claimed did not exist. The second code-review round caught it.

## Candidate rule

When a parse failure on untrusted input is logged, log a sanitized, bounded description (or a fixed message plus a safe classifier) and do not pass the raw exception (whose message embeds the input) to the logger. A comment that asserts sanitization must be checked against the actual logger arguments. The epic already closed a CWE-117 duplication item (#863), so this may reinforce an existing TokenSheriff lesson or convention rather than form a new one.
