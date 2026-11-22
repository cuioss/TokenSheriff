envelope_version=1
sender_type=plan
sender_id=plan-15-final-fix-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-21T12:45:11Z

# Candidate lesson: an operator-approved edit to a "passes unchanged" test needs criteria-authorize before the verifier accepts the criterion

**Signal:** Q-Gate / functional-review (6-finalize criteria integrity)
**Records:** decision log 10d919 (2026-11-21T10:19:39Z, `(criteria-integrity) Operator decision authorizes changing frozen criterion D2: '... BackChannelHttpBodyPhaseTest and BackChannelClientExchangeFailureTest pass unchanged.' ... The change authorized: BackChannelHttpBodyPhaseTest is edited ... so it no longer passes unchanged`); functional-review residual 981cb5 / fa9a1b (D2.R3: "criterion C3 says BackChannelHttpBodyPhaseTest passes unchanged; edited ... on the operator decision recorded through criteria-authorize for D2 C3; the be9a2550 fixture-hold edit is not literally named in that decision").

## What happened

At the merge-queue ejection the operator chose "Fix the probe first". The question had stated that this edits a test the spec says to leave as it is. The answer alone did not satisfy the frozen D2 criterion: the review kept treating "passes unchanged" as literal. A `criteria-authorize` record was written, and from then on the functional-review residual cites it. The later fixture-hold edit (be9a2550, from CodeRabbit a9db38) is still flagged as "not literally named" in that authorization. So an authorization scoped to one edit doesn't stretch to the follow-up edits the same decision leads to.

## Why it may be worth carrying

When an operator answer changes a frozen criterion, record `criteria-authorize` straight away as part of acting on the answer. Word it to cover the whole change ("the probe may be edited to ..."), not one commit, or each follow-up edit reopens the residual.
