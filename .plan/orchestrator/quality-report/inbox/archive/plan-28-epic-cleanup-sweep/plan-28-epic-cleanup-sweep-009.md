envelope_version=1
sender_type=plan
sender_id=plan-28-epic-cleanup-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-11T11:55:22Z

# Candidate lesson: SVG diagram corrected from geometry only, never rendered

Source: Q-Gate finding a01a53 (phase 6-finalize, functional-review residual D5.R1, error, resolution taken_into_account, superseded by the functional review round at 31e39c3d)

The PKCE diagram `doc/resources/diagrams/client-auth-code-pkce.svg` was corrected from a geometric check alone. The D5 criterion required a render, and no SVG converter was installed. Functional review looped back (TASK-017), and execution paused until the operator installed `rsvg-convert` (apt package librsvg2-bin).

Possible rules: (1) a diagram fix criterion that says "render" needs a real render (rsvg-convert or a headless browser) during execute. (2) Check for the required tool at plan time so the loop-back does not block on operator package installation.
