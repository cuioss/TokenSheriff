envelope_version=1
sender_type=plan
sender_id=plan-13-renumber-commons-validation
epic=client-framework-aspects
kind=candidate-lesson
created=2026-11-24T22:40:10Z

# Candidate lesson: whole-tree Maven gates get OOM-killed when another session builds at the same time

Source: run observation in the phase-6-finalize pre-push-quality-gate. The work log shows every build resolving `resolved=in_process, reason=disabled, mechanism=in_process_fallback`, so the build server was not serializing builds.

Observation: the kernel OOM killer stopped the whole-tree quality-gate build because a Maven build from another Claude session was running on the same machine at that moment. It worked after waiting for the other build to finish and rerunning with `-T 4` (reduced parallelism).

Corrective rule: when a whole-tree build dies with an OOM kill or exit 137 and no test failure, check for builds from other sessions before treating it as a code failure. Rerun after they finish, with less parallelism (`-T 4`). With the build server disabled, the machine-global slot cap does not prevent memory contention between sessions. Enabling the build server, or a lower whole-tree thread count, is the structural fix.

Transferability: any parallel-plan workflow in this epic that runs several sessions on one machine.
