envelope_version=1
sender_type=plan
sender_id=plan-12-open-items-sweep
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-19T06:28:38Z

# Candidate lesson: weakened_test detector flags required test renames and gained-assertion restructures

- Signal: Q-Gate (6-finalize), rule `functional-review`, 12 `weakened_test` findings, all accepted by the operator:
  author-filed d07e82, 8af14b, 0e7c07, 235495, 3cfd32, 716262 and detector-filed 32a5b3, 3cb5b8, 237898, 5e0017, b7740e, 99651d

## What happened

The functional review flagged six test files as weakened. Five were renames required by the pre-1.0 wording rule
(`...ByteIdentical`, `shouldKeepTwoArgumentFormUnchanged`), one replaced two `shouldRefuseControlCharacterValue` tests with
`shouldNotEchoRefusedValue` after an operator decision, and `DiscoverySpecIT.shouldServeCachedEngineResolution` was
restructured and gained assertions (PLAN-04 nitpick fix). The assertion-count detector then filed a second, duplicate
finding per file ("N assertion(s) present at 27bb72ff are gone from <old method name>"), because it tracks assertions by
the old method name. Each pair needed an operator acceptance, and the round could not close until the accepted removals
were recorded.

## Evidence

- Findings listed above; shared resolution_detail "Operator accepted six flagged tests as intended ...".
- Decision log d1f13d ("F13-F18 look like rename artifacts"), 0c46cc ("6 blocking weakened_test findings remain (operator
  accepted, not yet in the accepted-removal record)").

## Observation for the epic

A rename moves assertions to a new method name; a detector keyed on the old name reads that as removal, and the author
and the detector each file one finding for the same file.
