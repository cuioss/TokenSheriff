envelope_version=1
sender_type=plan
sender_id=plan-03-client-key-material-config-validators
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-17T14:56:51Z

component=token-sheriff-validation
category=anti-pattern
status=active

# An allow-list host validator must be derived from what `URI.getHost()` returns, not from a hostname grammar

A host allow-list entry is only useful if it can equal what `URI.getHost()` yields for a real URL, so the validator has to be built from `getHost()` semantics before any grammar rule is chosen.

## What happened

In plan plan-03-client-key-material-config-validators (PR #915), `EgressHostValidator` was first written as a plain LDH hostname check. CodeRabbit then found two inputs it decided wrongly, and both were fixed with regression tests:

- `idp.example.com.` (one terminal root dot) was refused, although `URI.getHost()` reports a trailing-dot host with the dot. The validator now admits exactly one terminal root dot and keeps it in the stored form so textual matching still works.
- `idp.123` and `idp.1example` were admitted, although `java.net.URI` only parses a dotted name as a host when its last label starts with a letter. Such an entry is stored but can never match, and `EgressPolicy.check` rejects the URL before it reaches the allow-list. The validator now refuses them at configuration time. A single-label `1example` stays admitted, which was a recorded deviation from the reviewer's suggestion.

## Rule

When a validator guards a value that is later compared with the output of a JDK parser, enumerate that parser's accept and reject cases first (trailing dot, last-label rule, bracketed IPv6, case) and put each in the refusal and admission tests. A grammar written from RFC knowledge alone drifts from the parser in both directions.
