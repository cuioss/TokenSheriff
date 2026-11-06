envelope_version=1
sender_type=plan
sender_id=plan-18-doc-accuracy
epic=quality-report
kind=candidate-lesson
created=2026-11-06T18:45:31Z

# Candidate lesson: automatic-review signal - doc claim missed a conditional code branch; coderabbit quota stalled required-bot participation

**Source record**: automatic-review signal (signal_automated_review_count=1) - review-bot findings remediated in-run on PR #877, e.g. pr-comment 29615c (sourcery-ai, resolution fixed), plus the coderabbit participation stall.
**Suggested component**: plan-marshall:automatic-review (quota stall) / pm-documents:ref-documentation (doc-accuracy check)
**Suggested category**: improvement

## What happened

1. **Slipped doc-accuracy defect.** The rewritten client docs (package-info, client requirements, retrieval-flow spec, threat model) stated the advertised `introspection_endpoint` scheme is HTTPS-checked. `DiscoveryResolver.validateEndpointSchemes` returns early when the issuer is not https, so the check only applies to https issuers. sourcery-ai caught it; the fix rewrote the same sentence in four places. A plan whose whole purpose was "claims contradicting code" introduced a new claim that ignored a conditional branch in the code it described.
2. **Bot quota stall.** coderabbit's free-tier quota refusal stalled the required-bot participation; it only progressed after the `review_rate_window_await` parameter was enabled and the bot was re-triggered.

## Corrective rules

1. When a doc states a security check, verify the claim against every early-return / guard in the checking method, and state the condition under which the check applies.
2. automatic-review should recognise a review-bot quota/rate-limit refusal comment as a distinct state (not "participated" and not "pending forever") and either enable the rate-window await automatically or surface it to the operator immediately with the re-trigger instruction, rather than stalling until someone notices.
