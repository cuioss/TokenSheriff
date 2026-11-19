envelope_version=1
sender_type=plan
sender_id=plan-12-open-items-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-19T06:30:42Z

# Candidate lesson: a long wait region let main move under a ready PR, forcing a conflict rebase and a second full finalize pass

- Signal: process observation 6 of the run (decision log)

## What happened

The last pre-rebase review rounds closed at 97096a5e on 2026-11-18 around 22:46Z with zero pending findings. Branch cleanup
ran the next morning (05:21Z). By then main had moved: #926 (JDK 27 malformed Content-Length framing in BackChannelHttp
and TokenEndpointClient — the plan's two core client files) and #927 (workflow update) had landed, and #928 (workflow
update) landed before the merge. The classifier answered `overlap_with_content_conflict`, `needs_user`; the operator chose
rebase and force-push with conflict resolution. Because the head changed, every head-dependent step re-ran: pre-push gate
re-stamp, a full functional-review round, a full code-review round, ci-verify, automatic review and sonar-roundtrip. The
merged code also opened a new documentation lag (advisory D3.R5: COMMONS-17, transport.adoc and ADR-0031 name only the
JDK 26 IllegalArgumentException form, not the JDK 27 ProtocolException chain).

## Evidence

- Decision log 004e97 (22:46:03Z, nothing pending), a1c6a7 (05:21:25Z, upstream_commits=2 #926 #927, operator chose
  rebase), 5041de (loop back so every head-dependent step re-runs), e1db8b, 8855b7, d83484, c15536 (D3.R5), 981f10, 96775c
  / 57b5b1 / 279e51 (05:56-06:02Z re-runs), 83992d (merge routing).
- Main history: ec096319 (#926), d79da14e (#927), e53f3e4f (#928), then ae493e00 (#925).

## Observation for the epic

Plans in the same epic that touch the same files land while a PR sits between "review closed" and "merge"; the gap here
was about seven hours, and it cost one conflict resolution plus one full finalize pass.
