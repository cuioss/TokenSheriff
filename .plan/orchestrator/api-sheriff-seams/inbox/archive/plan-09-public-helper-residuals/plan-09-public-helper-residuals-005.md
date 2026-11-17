envelope_version=1
sender_type=plan
sender_id=plan-09-public-helper-residuals
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-17T16:31:54Z

# Candidate lesson: review_completeness bare-vs-pair bot flags confused in both directions

**Signal source:** script-failure cluster `plan-marshall:automatic-review:review_completeness` (failure_kind=script_internal_failure, exit_code=1, `error: malformed_bot_flag`).

**Records (three seconds apart, branch-cleanup pre-merge gate, 2026-11-17):**
- 16:08:09Z: `--stale-participation-bots expects bot_kind:evidence_kind pairs but received the token 'coderabbit', which is not a pair.`
- 16:08:12Z: `--finished-review-bots expects bare bot_kind tokens but received the pair-shaped token 'coderabbit:review_body'.`

**Observation:** The caller first passed a bare `coderabbit` to a pair-form flag. It then "fixed" this by passing the pair `coderabbit:review_body` to a bare-form flag. So it confused the two flag families in both directions within one gate evaluation. The script rejected both correctly, and its messages explain why: a silently dropped bare token would resolve the bot to `absent` instead of `participated_stale`. The caller still could not tell from the call site which flags take pairs.

**Possible corrective action (for the orchestrator to judge):** List the pair-form and bare-form flags of `review_completeness` in one table next to the gate step that calls it, with one worked example per family (for example `--stale-participation-bots coderabbit:review_body` and `--finished-review-bots coderabbit`). Or have the verb's error name the flag family the token's shape belongs to, so a second wrong guess is not needed.

**In-run impact:** None lasting. The gate then evaluated and PR #916 merged through the merge queue (67f8e480).
