envelope_version=1
sender_type=plan
sender_id=plan-14-client-adversarial-tests
epic=quality-report
kind=candidate-lesson
created=2026-11-05T13:00:28Z

## Candidate lesson: multi-line Content-Type bypass surfaced only in the second review round

**Signal source:** automated review (review-bot finding remediated in-run).

**Observation:** In plan-14 (PR #862, client adversarial tests) a Content-Type validation bypass via a multi-line (folded / CR-LF-embedded) header value was not caught by the first adversarial pass nor the first review round; it was raised only in the second automated-review round and then fixed in-run.

**Shape:** defect + corrective action (adversarial-coverage gap: header-injection variants not enumerated up front).

**Suggested corrective rule:** when authoring adversarial tests for HTTP header handling in the client (Content-Type, and any header parsed for security decisions), enumerate line-break / header-folding variants (CR, LF, CRLF, obs-fold continuation, embedded NUL) as a standard attack class in the first pass rather than relying on review rounds to surface them; the cui-http-testing attack-database pattern is the natural home for that enumeration.

**Candidate component(s):** TokenSheriff client module adversarial test suite; possibly `pm-dev-java-cui:cui-http-testing` (classification deferred to orchestrator).
