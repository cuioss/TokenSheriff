envelope_version=1
sender_type=plan
sender_id=plan-25-epic-leftover-sweep
epic=quality-report
kind=candidate-lesson
created=2026-11-09T13:27:43Z

component=plan-marshall:phase-6-finalize
category=bug
created=2026-11-09
source_record=run event (dispatcher-reported): review loop-back close at later heads

# Review loop-back close needs pending findings, but re-filed accepted findings come back "kept"

## Observation

The review step's loop-back close expects pending findings to accept. At later heads, a re-filed finding that had already been accepted comes back as `kept`, not pending, so `--accept` has nothing to act on. The only close that works at those heads is one with no `--accept`.

## Candidate rule

The close verb should treat a kept (already-accepted) finding named in `--accept` as accepted, or say clearly that no `--accept` is needed. The workflow doc should state the no-accept form for later heads.
