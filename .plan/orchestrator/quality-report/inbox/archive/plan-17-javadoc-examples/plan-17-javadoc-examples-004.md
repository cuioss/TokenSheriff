envelope_version=1
sender_type=plan
sender_id=plan-17-javadoc-examples
epic=quality-report
kind=candidate-lesson
created=2026-11-06T20:45:28Z

component=plan-marshall:build-maven
category=improvement
title=A whole-tree `test-compile` gate is not performable in a multi-module build whose test-jar is made at package phase

## Problem

In TokenSheriff the `token-sheriff-validation` module publishes a test-jar at the `package` phase. A whole-tree `test-compile` therefore fails in the downstream modules with "package ...validation.test does not exist" (20 errors, none in files the plan touched), while the full `verify` run is green (3852 tests).

A gate that reads that red result as a failure blocks a Javadoc-only change for a reason that has nothing to do with it. PLAN-17 recorded the arm as "not performable" with the provenance (the pom `test-jar` lines, the branch touching none of those modules, the green `verify`), not as passed and not as failed.

## Rule

The module profile should mark `test-compile` as not performable when a sibling test-jar is only built at package time, so the gate stops offering it, and the canonical command set should carry that fact instead of each plan re-deriving it.

## Evidence

PLAN-17 execute phase, `verify -Ppre-commit` green at 3852 tests.
