# Token-Sheriff

## Status

<img align="right" width="300" src="doc/resources/Sheriff.png" alt="Token-Sheriff">

**Build & Quality**

[![Java CI with Maven](https://github.com/cuioss/TokenSheriff/actions/workflows/maven.yml/badge.svg?branch=main)](https://github.com/cuioss/TokenSheriff/actions/workflows/maven.yml)
[![Integration Tests](https://github.com/cuioss/TokenSheriff/actions/workflows/integration-tests.yml/badge.svg?branch=main)](https://github.com/cuioss/TokenSheriff/actions/workflows/integration-tests.yml)

[![Last Build](https://img.shields.io/github/last-commit/cuioss/TokenSheriff/main)](https://github.com/cuioss/TokenSheriff/commits/main)
[![License](https://img.shields.io/badge/license-Apache_2.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)
[![Maven Central](https://img.shields.io/maven-central/v/de.cuioss.sheriff.token/token-sheriff-parent.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/de.cuioss.sheriff.token/token-sheriff-parent)

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=cuioss_TokenSheriff&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=cuioss_TokenSheriff)
[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=cuioss_TokenSheriff&metric=ncloc)](https://sonarcloud.io/summary/new_code?id=cuioss_TokenSheriff)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=cuioss_TokenSheriff&metric=coverage)](https://sonarcloud.io/summary/new_code?id=cuioss_TokenSheriff)

[![OpenSSF Best Practices](https://www.bestpractices.dev/projects/11849/badge)](https://www.bestpractices.dev/projects/11849)
[![CodeQL](https://github.com/cuioss/TokenSheriff/actions/workflows/github-code-scanning/codeql/badge.svg)](https://github.com/cuioss/TokenSheriff/security/code-scanning)

**Performance Benchmarks**

[![JMH Benchmarks](https://github.com/cuioss/TokenSheriff/actions/workflows/benchmark.yml/badge.svg)](https://github.com/cuioss/TokenSheriff/actions/workflows/benchmark.yml)
[![Last Benchmark Run](https://img.shields.io/endpoint?url=https://cuioss.github.io/TokenSheriff/benchmarks/badges/last-run-badge.json)](https://cuioss.github.io/TokenSheriff/benchmarks/)

*Micro Benchmarks*

[![JWT Performance Score](https://img.shields.io/endpoint?url=https://cuioss.github.io/TokenSheriff/benchmarks/badges/performance-badge.json)](https://cuioss.github.io/TokenSheriff/benchmarks/micro/)
[![Performance Trend](https://img.shields.io/endpoint?url=https://cuioss.github.io/TokenSheriff/benchmarks/badges/trend-badge.json)](https://cuioss.github.io/TokenSheriff/benchmarks/micro/trends.html)

*Integration Benchmarks*

[![Integration Performance](https://img.shields.io/endpoint?url=https://cuioss.github.io/TokenSheriff/benchmarks/badges/integration-performance-badge.json)](https://cuioss.github.io/TokenSheriff/benchmarks/integration/)
[![Integration Trend](https://img.shields.io/endpoint?url=https://cuioss.github.io/TokenSheriff/benchmarks/badges/integration-trend-badge.json)](https://cuioss.github.io/TokenSheriff/benchmarks/integration/trends.html)

[Understanding Performance Metrics](benchmarking/doc/performance-scoring.adoc)

## What is it?

A comprehensive library for validating JWT tokens in multi-issuer environments with a focus on offline validation.

## Motivation

### Why another JWT-Library?

This project started as an instrumentation of [SmallRye JWT](https://github.com/smallrye/smallrye-jwt), then shifted to [JJWT](https://github.com/jwtk/jjwt) with the goal to implement robust multi-issuer handling. With a strong focus on security (see [Requirements](doc/validation/requirements.adoc)), the project evolved through several iterations until it became an entirely new library with its own validation and parsing implementation (JJWT remains only as a test dependency for token generation). The main differentiator is the comprehensive approach to security in multi-issuer environments.

### The Challenge

Modern microservice architectures often need to validate JWT tokens from multiple identity providers without making synchronous calls to authorization servers. This library addresses several key challenges:

### Why Offline Validation?

- **Performance**: No network round-trips for token validation, enabling sub-millisecond validation times
- **Resilience**: Service remains functional even when identity providers are temporarily unavailable
- **Scalability**: Validation scales with your application, not limited by identity provider capacity
- **Cost**: Reduces load on identity providers, avoiding rate limiting and additional infrastructure costs

### Key Problems Solved

- **Multi-Issuer Complexity**: Seamlessly handle tokens from Keycloak, Auth0, Azure AD, and other providers in a single application
- **Key Rotation**: Automatic JWKS key fetching and caching with configurable refresh strategies
- **Security**: Protection against common JWT vulnerabilities through comprehensive validation pipeline
- **Configuration Overhead**: OpenID Connect Discovery for automatic configuration from well-known endpoints

> [!NOTE]
> The `token-sheriff-validation` library focuses on JWT validation — it will never create or acquire tokens, only validate them. Active token acquisition (the OIDC/OAuth client flows) is a distinct concern, shipped separately as the `token-sheriff-client` engine.

## Quick Start

### Quarkus Integration (Recommended)

For Quarkus applications, use our dedicated extension for seamless integration:

```xml
<dependency>
    <groupId>de.cuioss.sheriff.token</groupId>
    <artifactId>token-sheriff-validation-quarkus</artifactId>
</dependency>
```

The [Quarkus Extension](token-sheriff-quarkus-parent/README.adoc) provides:

- Minimal configuration with zero-configuration for sensible best-practice security settings
- CDI injection of validated tokens
- Automatic metrics and health checks
- Native image support for GraalVM
- Dev UI integration for testing

**Minimal Configuration Example (using OpenID Connect Discovery)**

```properties
# application.properties
sheriff.token.issuers.keycloak.jwks.http.well-known-url=https://keycloak.example.com/realms/master/.well-known/openid-configuration
```

```java
@GET
@Path("/data")
@BearerAuth(requiredScopes = {"read"}, requiredRoles = {"user"})
public Response getData() {
    // Only business logic - security handled automatically by interceptor
    // If validation fails, error response is returned automatically
    return Response.ok(data).build();
}
```

The extension also supports standard [MicroProfile JWT Auth 2.1](https://download.eclipse.org/microprofile/microprofile-jwt-auth-2.1/microprofile-jwt-auth-spec-2.1.html) injection — every validated token is a `JsonWebToken` and `Principal`:

```java
@Inject
JsonWebToken callerPrincipal;  // MP-JWT standard

@GET
@BearerAuth(requiredScopes = {"read"})
public Response getData() {
    String userName = callerPrincipal.getName();  // UPN fallback: upn → preferred_username → sub
    Set<String> groups = callerPrincipal.getGroups();
    return Response.ok("Hello " + userName).build();
}
```

For details, see the [MicroProfile JWT Compatibility](doc/validation/specification/microprofile-jwt-compatibility.adoc) specification and the [Quarkus Extension documentation](token-sheriff-quarkus-parent/README.adoc).

For a complete working example, see the [integration tests module](token-sheriff-quarkus-parent/token-sheriff-quarkus-integration-tests/README.adoc).

### Standalone Library

For non-Quarkus applications, use the core validation library:

```xml
<dependency>
    <groupId>de.cuioss.sheriff.token</groupId>
    <artifactId>token-sheriff-validation</artifactId>
</dependency>
```

```java
// Create validator with OIDC Discovery
TokenValidator validator = TokenValidator.builder()
    .issuerConfig(IssuerConfig.builder()
        .httpJwksLoaderConfig(HttpJwksLoaderConfig.builder()
            .wellKnownUrl("https://your-issuer.com/.well-known/openid-configuration")
            .build())
        .expectedAudience("your-client-id") // Add expected audience
        .build())
    .build();

// Validate token
AccessTokenContent accessToken = validator.createAccessToken(AccessTokenRequest.of(tokenString));
```

### Client Engine

To *acquire* tokens — the active, confidential-client side — use the framework-agnostic `token-sheriff-client` engine (it validates every token it retrieves via `token-sheriff-validation`):

```xml
<dependency>
    <groupId>de.cuioss.sheriff.token</groupId>
    <artifactId>token-sheriff-client</artifactId>
</dependency>
```

```java
// Configure once per issuer; endpoints resolve via OIDC discovery
String secret = System.getenv("OIDC_CLIENT_SECRET"); // supply via config or environment
ClientConfiguration config = ClientConfiguration.builder()
    .issuer("https://issuer.example.com/realms/demo")
    .clientId("my-confidential-client")
    .clientSecret(secret)
    .authMethod(ClientAuthMethod.CLIENT_SECRET_BASIC)
    .scope("openid")
    .build();
```

See the [token-sheriff-client README](token-sheriff-client/README.adoc) and the [client capability documentation](doc/client/README.adoc) for the flows, client-authentication methods, and token-lifecycle handling.

## Core Features

- **Multi-issuer support** for handling tokens from different identity providers
- **Automatic JWKS key management** with rotation support
- **OpenID Connect Discovery** for automatic configuration
- **Type-safe token parsing** with strongly typed Access, ID, and Refresh tokens
- **Comprehensive security** with configurable validation pipeline
- **High performance** with sub-millisecond validation and built-in caching
- **Production ready** with extensive testing against Keycloak

## Architecture

For detailed architectural information, see:

- [Architecture Reference](doc/validation/architecture.adoc) - Validation pipeline, components, and design
- [Component map](doc/resources/diagrams/validation-components.svg) - Visual architecture overview

### Modules

| Module | Description |
| --- | --- |
| [`token-sheriff-validation`](token-sheriff-validation/README.adoc) | Core JWT validation engine. Also carries the `commons` base layer (`de.cuioss.sheriff.token.commons.*`: transport, error model, events, metrics), enforced by ArchUnit. |
| [`token-sheriff-client`](token-sheriff-client/README.adoc) | Framework-agnostic OIDC/OAuth **client** engine: OIDC discovery, authorization-code / client-credentials / refresh flows with PKCE and PAR, client authentication (`client_secret_basic`/`client_secret_post`, `private_key_jwt`, mTLS), DPoP proof generation, token lifecycle management (store, refresh scheduling, revocation), logout/end-session, and userinfo. Depends on `token-sheriff-validation`. |
| [`token-sheriff-validation-quarkus`](token-sheriff-quarkus-parent/README.adoc) | Quarkus extension (runtime + deployment) for token validation. |
| `token-sheriff-client-quarkus` | Quarkus extension (runtime + deployment) that wires the client engine into Quarkus: CDI producer (`TokenSheriffClientProducer`), configuration mapping (`ClientRuntimeConfig`), and exception mapping, building on the validation extension. |
| [`bom`](bom/pom.xml) | Bill of Materials for all Token-Sheriff artifacts. |
| [`benchmarking`](benchmarking/README.adoc) | JMH micro-benchmarks and WRK integration load tests. |

## Documentation

- [Documentation Hub](doc/README.adoc) - Complete guide to all documentation
- [Client Capability](doc/client/README.adoc) - The OIDC/OAuth client engine: flows, client authentication, and token lifecycle
- [Usage Guide](token-sheriff-validation/README.adoc) - Detailed usage examples
- [Requirements](doc/validation/requirements.adoc) - Functional and non-functional requirements
- [Threat Model](doc/validation/threat-model.adoc) - Security analysis
- [MicroProfile JWT Compatibility](doc/validation/specification/microprofile-jwt-compatibility.adoc) - MP-JWT 2.1 integration and rationale
- [Multi-IDP Testing](doc/validation/specification/multi-idp-testing.adoc) - Testing with multiple OIDC providers

For configuration details including runtime dependencies and test support, see the [Token-Sheriff Core documentation](token-sheriff-validation/README.adoc).

## Performance

The library is continuously benchmarked with results published to GitHub Pages:

- [Micro-benchmarks](benchmarking/benchmark-core/README.adoc) - In-memory performance testing
- [WRK Load Testing](benchmarking/benchmark-integration-wrk/README.adoc) - HTTP-based load testing with WRK
- [Performance Metrics](benchmarking/doc/performance-scoring.adoc) - Understanding the scoring system
- [Integration Benchmark Analysis (March 2026)](benchmarking/doc/Analysis-03.2026-Integration.adoc) - WRK HTTP load testing analysis
- [Micro-Benchmark Analysis (March 2026)](benchmarking/doc/Analysis-03.2026-Micro.adoc) - JMH library performance analysis
