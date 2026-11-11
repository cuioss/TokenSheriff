envelope_version=1
sender_type=plan
sender_id=plan-15-validation-test-gaps
epic=quality-report
kind=landing
created=2026-11-06T08:17:44Z

## What landed

plan-15-validation-test-gaps shipped as #867 (merged).

```landing-facts
schema=landing-facts/1
plan_id=plan-15-validation-test-gaps
epic=quality-report
pr=#867
merge_state=merged
cleanup_owed=false
deliverables_total=6
deliverables_done=5
total_tokens=21079068
total_wall_seconds=56495.0
harness=claude
model=claude-sonnet-5-5
effort=unknown
plugin_version=0.1.2046
executor_version=0.1.2046
steps=finalize-step-sync-baseline:done,finalize-step-security-audit:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,adr-propose:skipped,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done
```

## Residue

- **TV-4 is NOT closed (deliverable 5 partly undone, by operator decision).** The outline asked to remove the valueOf/ordinal-only enum tests (`LoaderStatusTest`, `MeasurementTypeTest`, `EventCategoryTest`). The functional review flagged each removal as a blocking weakened_test finding, and the operator chose to restore them (commit 7b8ed2a7), so those three files are byte-identical to `main`. The `TestTokenHolder` Javadoc part of deliverable 5 (TV-7) is done. `deliverables_done=5` counts deliverable 5 as not done for that reason.
- **Review closes are operator overrides at specific heads.** Both the functional review and the code review were closed by the operator (`may_close: operator_override`), because two post-push criteria (D1.C3 Sonar issue gone, D6.C2 PR body line) cannot be observed before the push. The Sonar issue (`java:S8786`, `AZ7xhrgAXGjoVdkwztNZ`) was confirmed gone after the push: 0 new-code issues.
- **Production changes beyond the outline.** The DSL-JSON truncated-escape leak (`ArrayIndexOutOfBoundsException` instead of `IOException`) was fixed at the four validation sites plus, by operator decision, `DpopProofValidator` and the three client sites (`ParClient`, `UserInfoClient`, `DiscoveryResolver`). `JwksParser` also sanitizes the `JWKS_INVALID_JSON` message (CWE-117). Other DSL-JSON sites that only catch `IOException` (`RevocationClient`, `JWKSKeyLoader`, `JweDecryptor`) were not reviewed.
- **Known test gap.** The JWE-header catch in `NonValidatingJwtParser` has no dedicated test.
- **Verification degradation.** The pre-push gate recorded a whole-tree `verify -Ppre-commit` (3852 tests) on the final head; per-bundle and bare `test-compile` were not run, because the client module's tests need the validation test-jar and cannot be verified with a bare `test` goal.
- **Token total is mixed-population.** `total_tokens` spans populations (`totals_tokens_population: mixed`); read it as a floor-style aggregate, not one measured figure.
- **Unreported in the candidate-lesson stream:** the CodeRabbit quota and rate-window waits during automatic-review, and the repeated build-queue `max_slots` config warning.
