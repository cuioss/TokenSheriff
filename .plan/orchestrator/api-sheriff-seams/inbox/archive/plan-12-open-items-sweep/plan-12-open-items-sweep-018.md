envelope_version=1
sender_type=plan
sender_id=plan-12-open-items-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-19T06:30:52Z

# Candidate lesson: closing full code-review rounds re-read only a subset of the footprint, relying on earlier byte-identical readings

- Signal: process observation 7 of the run (decision log; the validator reports the gap as `files_read`)

## What happened

Two closing code-review rounds were declared `round_kind=full` over 52 footprint files but re-read only the files that
had changed since the last fully read round:

- At 97096a5e: files_read=7 of 52. The other 45 were byte-identical to the round that closed at 0e84e8a5, which had read
  all 52.
- After the rebase, at 33e68ac0: files_read=12 of 52. The author re-read the merged client code in full and relied on
  earlier readings for the byte-identical rest; the JavaScript, CSS, AsciiDoc and Lombok rule packs were not loaded.

The verifier accepted both rounds and recorded the coverage in a note. A "full" label with partial reading is a judgement
the run made; the validator only reports the count.

## Evidence

- Decision log 169b7c (22:02:28Z, "this full round re-read 7 of 52 files (files_read=7)"), 981f10 (05:48:00Z,
  "files_read=12 of the 52 footprint files ... JavaScript, CSS, AsciiDoc and Lombok rule packs were not loaded").
- Decision log 2c69d3: advisories from the earlier round "not re-listed by this round (the diff leaves them unchanged, the
  author did not re-judge them)".

## Observation for the epic

There is no recorded rule for when a closing full round may reuse earlier byte-identical readings; the run decided it case
by case.
