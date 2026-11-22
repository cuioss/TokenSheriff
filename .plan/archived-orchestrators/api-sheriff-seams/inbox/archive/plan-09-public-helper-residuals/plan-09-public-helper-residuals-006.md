envelope_version=1
sender_type=plan
sender_id=plan-09-public-helper-residuals
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-17T16:32:05Z

# Candidate lesson: weakened_test counter misattributes a helper body and re-files at every head

**Signal source:** Q-Gate findings, 6-finalize (functional-review and code-review). All resolved in-run, `accepted`.

**Records:** 7bf2fc, a91c5a (f57edf20), ac6570 / ae034b (d4cf9828), 33295a / 95f3ad (476a420f), 000181 / bf3e4d (f0fdf6b6), f98f88 / 9a945f (57c29c23), f3374f / 1c5ff4 (rebased head 0926f3ad). That is 12 rows on `JwksEndpointHealthCheckIssuerStatusFailureTest.java`, all `weakened_test` on `ConfiguredIssuer::checkConfigured` (2 assertions).

**Observation:** The issuer-in-WARN assertion was moved into, and restored through, the helper `assertWarnNamesIssuer`, which all four pre-existing sanitise-exception tests still call (commit f57edf20). The weakened-test counter folds the three-line `assertSanitisedFailure` helper body into the method before it, so it reads two assertions as removed. The operator accepted this as a counter artefact at f57edf20. Even so, the identical row came back at every later head: once from functional-review and once from code-review, at each of six heads. Each time it had to be re-filed and re-accepted, even though the commits in between were mechanical (Sonar cleanup, wording only). Content discriminators differ between rounds (807d62, 4d4e63, 1d74c9, 81c703, 1d41bc, 4b3338, 27a95c, c1e457), so an earlier acceptance is never carried forward.

**Possible corrective action (for the orchestrator to judge):** (1) Fix method attribution in the assertion counter so a private helper's body is not credited to the method above it. (2) Carry an operator-accepted counter artefact forward across heads when the method and assertion delta are unchanged, keyed on something stable (file, method, delta) rather than a per-round discriminator.
