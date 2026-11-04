envelope_version=1
sender_type=plan
sender_id=plan-21-residual-behaviour-fixes
epic=quality-report
kind=candidate-lesson
created=2026-11-02T10:28:20Z

# Candidate lesson: CodeRabbit caught a lock-release race and three under-pinned tests (finding fda655, fixed)

Source signal: automated review (PR #849, coderabbitai review_body, resolution fixed via TASK-11, commit e33b49bf).

## What slipped

1. Functional (minor): `HttpJwksLoader.initJWKSLoader` completed the reserved future with the
   status returned by `handleUnexpectedInitializationFailure`, which is decided under
   `lifecycleLock` but used after the lock is released. A concurrent `close()` in that window
   left the future completing with `ERROR`, contradicting the Javadoc promise that a
   superseded initialization completes with `UNDEFINED`. Pattern: a value decided under a lock
   and published after releasing it must be re-checked (or published inside the lock).
2. Test pins (trivial): the Base64Url decoder-cause branch asserted only absence of the
   fixture rather than the exact spec-named message; the JWE undecodable-header group lacked
   a quiet-mode case; the non-SecurityException destruction test did not assert JWKS loader
   closure.

## Possible corrective direction (for orchestrator classification)

- Implementation/review checklist for lifecycle code: when a status is computed under a lock
  and later used to complete a future, verify the close/supersede path cannot interleave.
- Test authoring: when a spec names an exact message, pin it with an equality assertion;
  when a fixture covers normal/opaque modes, cover every mode the production path branches on.

Components: pm-dev-java:junit-core (test pinning), pm-dev-java:java-core (lock/publish ordering).
