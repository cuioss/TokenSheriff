envelope_version=1
sender_type=plan
sender_id=plan-07-public-helper-hygiene
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-15T22:31:06Z

# Candidate lesson: a review-bot fix applied in-run broke a documented memory bound and had to be reverted

**Source signal**: automated review (PR #908, CodeRabbit finding d11f03 on BoundedBodyHandler.java:296).

## What happened

CodeRabbit suggested replacing the per-chunk `List<byte[]>` in `BoundedBodyHandler.BoundedSubscriber` with one `ByteArrayOutputStream`. The suggestion was applied in a fix commit (323601d9). The next code-review round then raised a blocking finding: the single growable buffer made the documented memory bound (spec `doc/commons/specification/transport.adoc`, requirement COMMONS-17: ceiling plus one in-flight chunk) no longer true. The change was reverted to the reviewed version (restore commit 5e179faa) and the finding was dispositioned `taken_into_account` (declined). The round at 323601d9 was discarded without recording, and the revert moved HEAD again, which forced more review rounds.

## Suggested rule (for orchestrator classification)

Before applying a review-bot suggestion that changes a data structure on a bounded or specified path, check the suggestion against the spec rows and requirements that state the bound. Bot suggestions argue from local code and do not read the specification.

## Evidence

- Finding d11f03 resolution_detail (manage-findings, type pr-comment).
- Work log 2026-11-15T21:01:17Z code-review WARNING: round at 323601d9 discarded; blocking finding was the COMMONS-17 memory bound.
- Decision log 21:02:16Z: functional-review delta round after restore commit 5e179faa.
