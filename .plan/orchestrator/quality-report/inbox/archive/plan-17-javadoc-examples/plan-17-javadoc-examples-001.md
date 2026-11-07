envelope_version=1
sender_type=plan
sender_id=plan-17-javadoc-examples
epic=quality-report
kind=candidate-lesson
created=2026-11-06T20:45:26Z

component=pm-dev-java:javadoc
category=improvement
title=Javadoc prose that states behaviour must be traced through the code path, fallbacks and empty inputs included

## Problem

PLAN-17 rewrote Javadoc usage examples so they compile. The examples were checked against signatures, but the accompanying PROSE (cookie source, precedence, JSON-null handling, 304 handling) was first written from names and intent. The first code review found two blocking precedence errors (the Authorization header is tried first and the cookie is only the fallback), and CodeRabbit then found nine more behaviour claims that were wrong at the edges: an empty `Bearer ` value gives INVALID_REQUEST with no cookie fallback, JSON null in `StringSplitterMapper`, the retired sets in `jwks/http/package-info`, and `initFuture.join()` in `JwksLoaderFactory`. Ten of eleven comments needed a fix commit.

## Rule

When Javadoc states how something behaves (precedence, fallback, error mapping, null handling), read the branch that implements it and cover the empty, null and exceptional inputs before writing the sentence. A compile-check of the example proves the example, not the prose around it.

## Evidence

PLAN-17 commits b252595f (precedence), 17f564db (empty Bearer), 3b1e11e5 (null, 304, loader join), PR #878.
