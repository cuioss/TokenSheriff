envelope_version=1
sender_type=plan
sender_id=plan-13-log-transport-hygiene
epic=quality-report
kind=candidate-lesson
created=2026-11-05T13:20:15Z

# Log-injection neutralisation applied piecemeal; unsanitized log records remain

**Defect**: Security-audit and CodeRabbit hardening drove three loop-back rounds on log-injection neutralisation (ErrorTextSanitizer) in newly added log records, e.g. CodeRabbit finding c8397c (the discovered issuer rendered unsanitized in JWKS_LOAD_FAILED / JWKS_KEYS_UPDATED, fixed as TASK-14). Pre-existing unsanitized logging of remote-supplied text remains in `KeyProcessor`, `JWKSKeyLoader` (DUPLICATE_JWKS_KID kid) and `JwksParser`.

**Corrective action**: Sweep all log records that render remote-supplied text (issuer, kid, error text from HTTP/discovery/JWKS) through ErrorTextSanitizer in one pass, and add a guard test that enumerates log records by placeholder so a new unsanitized remote-text argument fails the build. Recommended as a follow-up plan in this epic.

**Evidence**: `manage-findings list --plan-id plan-13-log-transport-hygiene --type pr-comment --resolution fixed` (3 review-bot findings, incl. c8397c), security-audit loop-backs in the work log.

**Suggested component**: token-sheriff-validation (project code), pm-dev-java-cui:cui-logging
