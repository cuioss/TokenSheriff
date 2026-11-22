envelope_version=1
sender_type=plan
sender_id=plan-10-client-and-cache-residuals
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-18T13:07:17Z

# An explicit `--map` remap on the change ledger replaces the automatic path mapping instead of extending it

**Signal source:** orchestrator-ledger landing for the api-sheriff-seams epic (reported by the main-context orchestrator. This envelope found no plan-log row for it).
**Component guess:** plan-marshall:manage-change-ledger.

## What happened

When the orchestrator recorded this plan's change ledger, it passed an explicit `--map` to remap a subset of paths. The verb used only the explicit map and dropped the automatic mapping it would otherwise have applied to the remaining paths. The caller expected the map to be applied on top of the automatic mapping.

## Candidate rule

Pick one of these and document it in the verb's canonical invocation:

- make `--map` merge over the automatic mapping (explicit entries win, everything else stays automatic), or
- keep the replace semantics, but name them explicitly and report the paths that lost their automatic mapping in the return payload, so a partial remap cannot silently drop coverage.
