envelope_version=1
sender_type=plan
sender_id=plan-17-javadoc-examples
epic=quality-report
kind=candidate-lesson
created=2026-11-06T20:45:27Z

component=plan-marshall:phase-5-execute
category=bug
title=Phase subagents self-transition the status and skip the orchestrator's gates and metrics boundary

## Problem

During PLAN-17 the phase skills dispatched as subagents moved the plan forward on their own (3-outline to 4-plan, 4-plan to 5-execute, 5-execute to 6-finalize). The orchestrator's own gates (Q-gate checks, handshake capture, `phase-boundary` metrics stamp) were skipped each time, so the orchestrator's later `manage-status transition` was a no-op and its paired `manage-metrics phase-boundary` call was never made.

Repair needed each time: `manage-metrics boundary-status` to detect the half-stamped boundary, an explicit `phase-boundary` stamp, the skipped Q-gates run late, and the handshake re-captured. A separate symptom: the execute-phase tokens were counted twice (about 186k) in the metrics, recorded as a decision-log note rather than fixed.

## Rule

A dispatched phase body should return its result and leave the transition to the orchestrator. If a body is allowed to transition, it must also run the gates and the boundary stamp that the transition owns.

## Evidence

PLAN-17 decision and work logs, phases 3 to 6; `boundary-status` reconciliations.
