envelope_version=1
sender_type=plan
sender_id=plan-09-dpop-parser-hardening
epic=quality-report
kind=candidate-lesson
created=2026-11-04T12:32:23Z

# Candidate lesson: a changed boundary semantic must be swept through every prose restatement

**Source plan:** plan-09-dpop-parser-hardening (PR #851)
**Signal:** Q-Gate findings 56df43, adc48c, 2fa0cc (6-finalize code-review, severity error, taken_into_account by commit a33f9aa9) plus the follow-on 5-execute scope_creep_warning 364f9d (accepted at finalize)
**Suggested component:** plan-marshall:phase-3-outline (declared footprint) / project docs convention

## What happened

The plan changed the token-expiry boundary from the strict form `exp + skew < now` to the new semantic. The code and tests were updated, but three textual restatements of the old boundary survived:

- `IssuerConfig.java` clock-skew Javadoc (lines 299 and 710)
- `JwtPropertyKeys.java` clock-skew key documentation (line 241)
- `doc/validation/specification/microprofile-jwt-compatibility.adoc` (line 97)

The finalize code review caught all three. Fixing them added files outside the declared footprint. Together with two review-driven test files and one threat-model row, that pushed the plan to 6 out-of-scope files (threshold 5), and the scope-creep warning had to be accepted by hand with a per-file rationale.

## Reusable rule

When a deliverable changes a documented semantic (a boundary, an inequality, a default, an accepted/rejected input class), search the repository for every prose restatement of the old rule before the outline is finalised: Javadoc, config-key docs, specification and threat-model AsciiDoc. List those files in the deliverable's affected files. Search for the literal old expression (here `exp + skew < now`, `exp + clockSkew < now`) and for its key names, not only for the changed method.

## Why it matters

- The stale wording is a documentation-correctness defect that only the final code review caught.
- Leaving these files out of the declared scope is what turns a routine doc correction into a scope-creep warning that someone has to accept by hand at the merge gate.
