envelope_version=1
sender_type=plan
sender_id=client-refresh-verification-discovery-cache
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-15T15:08:20Z

component=plan-marshall:manage-architecture
category=bug
source_plan=client-refresh-verification-discovery-cache
source_record=work-log 9d96b1 (2026-11-15T11:41:01Z, 6-finalize functional-review) script_failure argparse_rejection

# architecture search --content --literal rejected with argparse_rejection, but the logged detail names no cause

## What happened

During the first functional-review envelope the reviewer ran:

    architecture.py --plan-id client-refresh-verification-discovery-cache search --content --literal --pattern 'import de.cuioss.sheriff.token.client.flow'

The executor logged `exit_code=2 failure_kind=argparse_rejection`. The `detail` field held only the
command line, not the argparse message. Checked after the fact against the currently resolvable
script, the invocation looks valid: `--plan-id` is a top-level flag placed before the verb, and
`search` declares `--content`, `--pattern` and `--literal`. Two causes remain possible. First, the
envelope ran against a different installed bundle version in which `--literal` (or the
`search` verb) was not yet declared. Two plan-marshall cache versions are present on this machine
(0.1.2119 and 0.1.2134). Second, the single quotes reached argparse literally. The reviewer re-dispatched
and the step completed, so the content sweep was either retried or replaced.

## Why it matters

1. An argparse rejection whose logged detail is only the argv cannot be diagnosed after the fact. The
   signal-scan counts it as a script failure cluster, but nobody can tell what to fix.
2. If the cause was a version skew between the envelope's loaded skill docs and the executor's
   script, a reviewer can follow the docs exactly and still be rejected.

## Candidate rule

The execute-script `script_failure` record for `failure_kind=argparse_rejection` should always
capture argparse's own stderr message (the unrecognized or missing argument), not only the argv.
Separately, check that the skill docs an envelope loads and the script the executor runs come
from the same installed bundle version.
