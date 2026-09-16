# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Chaos is a Java 21 multi-module Maven framework built on Spring Boot 3.5 and Spring Cloud 2025.0. It provides enterprise building blocks following DDD and Hexagonal Architecture patterns. The base package is `com.michael.chaos.<module>`.

## Build Commands

```bash
# Full unit test suite
./mvnw -B test

# Test a single module (with upstream dependencies)
./mvnw -B -pl chaos-gateway/chaos-gateway -am test

# Integration tests (classes named *IT.java in src/integration-test/java; needs Docker)
./mvnw -B -Pchaos-integration-test verify

# Coverage report (JaCoCo, target/site/jacoco)
./mvnw -B -Pchaos-coverage verify

# Release governance checks
./mvnw -B -Pchaos-release -Dgpg.skip=true -DskipTests validate

# Structure governance (CI enforces this)
scripts/verify-structure.sh

# Regenerate the configuration reference after changing @ConfigurationProperties (CI runs --check after compile)
./mvnw -B -DskipTests compile && python3 scripts/generate-configuration-reference.py

# Smoke test the example JWT flow (auth + order + gateway)
scripts/smoke-examples-jwt.sh

# Generate each archetype and run `mvn verify` on the result (runs `./mvnw install -DskipTests` first: writes to ~/.m2)
scripts/verify-archetypes.sh
```

When reproducing CI locally, add `-s .mvn/settings-central.xml -gs .mvn/settings-central.xml`.

`.mvn/maven.config` applies `-T 1C`, `-ntp` and `-Drevision=<version>` automatically.

## Versions And Dependency Management

- All POM versions (including `<parent>` versions) are `${revision}`; the value lives in `.mvn/maven.config`. `flatten-maven-plugin` resolves it for installed/deployed POMs.
- `chaos-dependencies/pom.xml` is the single source of truth for dependency versions (Spring Boot, Spring Cloud, third-party libs, every chaos artifact). It has no parent. The root `pom.xml` (`chaos-parent`) uses it as parent and only configures plugins/profiles — do not add `dependencyManagement` to the root POM.
- Do not write `<version>` for chaos modules or BOM-managed libraries in module POMs.
- New published modules must be registered in `chaos-dependencies`; `chaos-architecture-tests` checks the BOM in both directions.
- In `chaos-autoconfigure`, every chaos feature library and third-party framework is `<optional>true</optional>`; each `*-starter` declares `chaos-autoconfigure` plus the feature library and frameworks it needs.
- Modules that contain `@ConfigurationProperties` classes (library modules or `chaos-autoconfigure`) must declare `spring-boot-configuration-processor` as optional.

## Architecture

The module dependency flows top-down. Directory layout (directory name = artifactId unless noted):

- **chaos-dependencies** — Public BOM and version source of truth.
- **chaos-foundation/** — `chaos-core` (request context `RequestContext`/`RequestContextSnapshot`, error codes `ErrorCode`/`ChaosException`/`BizException`, `ChaosHeaders`, idempotency contracts `core.idempotent` with dev-only in-memory impl in `core.idempotent.support`, `core.lock`, rate limiter SPI + in-memory impl `core.ratelimit`, governance metrics SPI `core.metrics` (`ChaosMetrics`/`ChaosMeterNames`, Micrometer impl lives in `chaos-trace`), framework message bundles `com/michael/chaos/core/i18n/messages/`, trusted-proxy client IP resolution `core.net.ForwardedClientIpResolver`) and `chaos-domain` (DDD primitives `domain.model.AggregateRoot`/`DomainEvent`, `domain.dto.PageQuery`/`PageResult`, `domain.cache` key strategy). `chaos-core` depends on no chaos module.
- **chaos-observability/chaos-trace** — W3C trace context propagation, MDC keys/`MdcSupport` and logback/log4j2 templates (`com.michael.chaos.trace.log`), OpenFeign trace interceptor (`trace.feign`), Micrometer observation filter and `MicrometerChaosMetrics` (`trace.monitor`; micrometer is optional).
- **chaos-audit/** — `chaos-audit` (audit events, incl. `AsyncAuditEventPublisher`) and `chaos-audit-jdbc` (JDBC audit persistence).
- **chaos-service** — Application-layer utilities: `TransactionExecutor`, `RetryExecutor`, `DomainEventPublisher`, `TraceContextTaskDecorator`.
- **chaos-web** — Servlet-layer: unified `Result` response wrapper, `GlobalExceptionHandler`, `TraceFilter`, `XssFilter`, `RateLimitInterceptor`, `IdempotentInterceptor`, `IdempotentResponseReplayFilter`, error message i18n (`web.i18n`). Requires `chaos-trace` (compile scope).
- **chaos-tenant** — Multi-tenancy: `TenantContext`, `TenantAccessValidator`, isolation modes, servlet adapter `tenant.servlet`.
- **chaos-security/** — `chaos-security-api` (Spring-free contracts: `LoginUser`, `LoginUserProvider`, `ChaosJwtClaims`, `JwtRevocationService`/`JwtTokenIds`, RBAC/ABAC model in `security.api.access`, `DataScopeContext`), `chaos-security` (servlet JWT/opaque-token resource server, permission/data-scope aspects), `chaos-authorization` (OAuth2 authorization server extensions) and `chaos-security-redis` (the single Redis JWT blacklist implementation plus Redis authorization-server stores). Gateway, MyBatis and Redis modules depend only on `chaos-security-api`.
- **chaos-gateway/** — `chaos-gateway` (Spring Cloud Gateway filters for JWT auth, tenant routing, rate limiting, blacklist, gray-tag, access logging; requires `chaos-trace`) and `chaos-gateway-nacos` (Nacos dynamic routes).
- **chaos-data/** — `chaos-mybatis` (MyBatis-Plus: tenant line, data scope, audit fill) and `chaos-redis` (Redisson: `DistributedLock` and `IdempotentRepository` implementations, rate limiter, bloom filter, delay queue).
- **chaos-mq/** — Reliable messaging with outbox pattern: `chaos-mq` (abstractions, outbox health in adapter package `mq.reliable.actuate`), `chaos-mq-kafka`, `chaos-mq-rocketmq`, `chaos-mq-jdbc` (outbox repository).
- **chaos-job** — Scheduled job support (`DistributedJobRunner`).
- **chaos-storage/** — Object storage: `chaos-storage` (port `ObjectStorageClient`, package `com.michael.chaos.storage`), `chaos-storage-minio`, `chaos-storage-oss`.
- **chaos-autoconfigure**, **chaos-starters/** — see below.
- **chaos-test-support** — Published test fixtures for business projects (`com.michael.chaos.test`): `@WithChaosContext`/`ChaosTestContext`, `TestLoginUsers`/`ChaosSecurityTestSupport`/`ChaosMockMvcSecurity`, `InMemoryRedisTemplates`, `ChaosContainers`, `ProductionSafetyTestSupport`. All framework deps optional; may only be depended on in `test` scope. Reuse it in framework tests instead of copying helpers.
- **chaos-architecture-tests** — Repository-level architecture tests (package `com.michael.chaos.architecture`; `LayerBoundaryArchitectureTest`, `DependencyDirectionArchitectureTest`, formerly `chaos-test`); not published.
- **chaos-python/chaos-tracing** — Standalone pip package (W3C trace context, gRPC interceptor, ASGI/WSGI HTTP middleware) for Python services; tested by the CI `python` job (ruff + mypy strict + pytest), not part of the Maven build. Its version must equal the Maven `revision`; `scripts/check-python-version.py` enforces this.

There are no `chaos-lock`, `chaos-idempotent`, `chaos-cache`, `chaos-feign`, `chaos-nacos`, `chaos-log`, `chaos-iam`, `chaos-common`, `chaos-file` or `chaos-monitor` modules. The OpenFeign trace interceptor lives in `chaos-trace` (`trace.feign`) and is registered by `chaos-autoconfigure` (`autoconfigure.cloud`); Nacos discovery/config conventions live in `autoconfigure.cloud.nacos`.

### Dependency Direction Rules

Enforced by `DependencyDirectionArchitectureTest` / `LayerBoundaryArchitectureTest` (see `chaos-docs/.../architecture.md` §1.1):

1. `chaos-core` depends on no chaos module and no Spring Web/Servlet/Security.
2. `chaos-security-api` depends only on `chaos-core` and imports nothing from `org.springframework`.
3. `chaos-gateway`, `chaos-gateway-nacos`, `chaos-mybatis`, `chaos-redis` may use `chaos-security-api` only — never `chaos-security`, `chaos-authorization` or `chaos-security-redis`.
4. `chaos-redis` contains no security concepts; Redis security implementations belong in `chaos-security-redis`.
5. `@AutoConfiguration` lives only in `chaos-autoconfigure`, which holds wiring only (no Filter/Interceptor/Repository/Service/Store/Registry implementations).
6. Starters have no Java code and never depend on examples, `chaos-test-support` or `chaos-architecture-tests`.
7. Only starters depend on `chaos-autoconfigure`.
8. `chaos-test-support` is only used in `test` scope.
9. Every module's packages live under its own base package `com.michael.chaos.<module>`; register the base package of any new module in `DependencyDirectionArchitectureTest.BASE_PACKAGES`.
10. Scenario starters (`chaos-web-service-starter`, `chaos-auth-server-starter`) aggregate capability starters only (plus Actuator/Prometheus); capability starters never depend on scenario starters.

### Auto-configuration And Starter Pattern

- **chaos-autoconfigure** — the single auto-configuration module (like `spring-boot-autoconfigure`). One package per feature: `com.michael.chaos.autoconfigure.<feature>` (`web`, `service`, `tenant`, `audit`, `audit.jdbc`, `security`, `security.redis`, `authorization`, `gateway`, `gateway.nacos`, `cloud`, `cloud.nacos`, `job`, `metrics`, `mq`, `mybatis`, `redis`, `storage`) plus `support` (`ProductionSafety`, `ProductionSafetyEnforcer`, `@UnsafeForProduction`). No classes in the root package. One `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`; do not use `spring.factories`.
- Every `@AutoConfiguration` must be guarded with class-level `@ConditionalOnClass` for its feature library and frameworks. `@Bean` method signatures (including `ObjectProvider<T>` generics) must not reference optional types: put such beans in nested `@Configuration` classes with their own `@ConditionalOnClass`, or route through a helper class only called when the types are present (see `AuthorizationRedisStores`). Servlet-only / reactive-only configurations declare `@ConditionalOnWebApplication`. `OptionalDependencyIsolationTest` physically removes jars and boots all auto-configurations — keep it green.
- `chaos-starters/chaos-<feature>-starter` — dependency-only POMs: `chaos-autoconfigure` + feature library + frameworks. Starters must not contain Java source. Library modules and examples must not depend on `chaos-autoconfigure` directly.

Starters come in two layers:
- **Scenario starters** (pick one per application): `chaos-web-service-starter` (servlet business service = web + security + tenant + audit + application), `chaos-gateway-starter` (API gateway), `chaos-auth-server-starter` (authorization server = authorization + Actuator/Prometheus).
- **Capability starters** (add as needed): `application`, `audit`, `audit-jdbc`, `authorization`, `cloud`, `cloud-nacos`, `cloud-reactive`, `gateway-nacos`, `job`, `mq`, `mybatis`, `redis`, `security`, `storage`, `tenant`, `web`.

`chaos-boot-parent` is the recommended parent for applications (inherits `chaos-dependencies`; Java 21, `-parameters`, surefire/failsafe, spring-boot `repackage` in pluginManagement). It must not inherit `chaos-parent` or carry framework release governance plugins.

### Configuration Usability

- Property descriptions come from field Javadoc on `@ConfigurationProperties` classes; value hints and Environment-only properties (e.g. `chaos.production-safety.*`) go in `src/main/resources/META-INF/additional-spring-configuration-metadata.json` of the module that owns the property.
- `chaos-docs/.../docs/configuration-reference.md` is generated by `scripts/generate-configuration-reference.py`; never edit it by hand.
- `chaos-docs/.../docs/templates/**/*.yml` are copy-paste config templates validated by `ConfigurationTemplatesTest` (keys must exist in metadata and bind to properties classes); keep scenario dev templates at ≤ 10 `chaos.*` keys.
- Startup diagnostics (`com.michael.chaos.autoconfigure.diagnostics`): the startup report and `/actuator/chaos` endpoint read `ChaosFeatureCatalog`; register every new auto-configuration there (`ChaosBuiltInDiagnosticRulesTest` checks). Custom checks implement `ChaosDiagnosticRule`.
- Configuration/startup errors thrown by framework code should be `ChaosDiagnosticException` (chaos-core) with a `ChaosDiagnostic` of problem / causes / fixes naming concrete starters or properties. `FailureAnalyzer` is not used because it requires `spring.factories`. See `docs/diagnostics.md`.

### Archetypes

`chaos-archetypes/` holds one Maven archetype per scenario starter (`chaos-archetype-web-service`, `-gateway`, `-auth-server`); not in the BOM.
- Generated projects use `chaos-boot-parent` with `${chaosVersion}` (defaults to the archetype's own version via `@project.version@` filtering of `archetype-metadata.xml` only).
- `application.yml` / `application-prod.yml` in `archetype-resources` must mirror `chaos-docs/.../docs/templates/<scenario>/` (only `spring.application.name` differs); change the docs template first. `ArchetypeArchitectureTest` enforces this.
- `archetype-resources` files are Velocity templates: no `##` and no `${...}` other than archetype variables in filtered files (use `Environment` instead of `@Value("${...}")`); files needing them (`application-prod.yml`, `README.md`) are `filtered="false"`.
- Architecture tests and `ProjectModel` skip `archetype-resources/` POMs.

### Example Services

`chaos-examples/` contains runnable services demonstrating the framework (not published):
- `example-auth-server` — OAuth2 authorization server
- `example-order-service` — DDD-layered order service (interfaces/application/domain/infra)
- `example-gateway` — API gateway

## Conventions

- Java 21, UTF-8, 4-space indent.
- Class naming by role: `*Properties`, `*Filter`, `*Service`, `*Repository`, `*Provider`, `*AutoConfiguration`.
- No `package-info.java` files (CI rejects them).
- No `spring.factories` (CI rejects them).
- No `TODO`, `FIXME`, `System.out`, or `printStackTrace` in committed code or config (CI rejects them; Markdown docs are not scanned).
- Unit tests: `*Test.java` in `src/test/java`. Integration tests: `*IT.java` in `src/integration-test/java` (Testcontainers is only allowed there).
- Production safety: in production mode (profiles `prod`, `production`, `prd` by default, or `chaos.production-safety.production-mode=true`) unsafe defaults such as `InMemoryRateLimiter`, `InMemoryIdempotentRepository`, `NoopJwtRevocationService` are rejected at startup — provide Redis-backed implementations. Escape hatches: `chaos.production-safety.fail-fast=false` (warn only) or `chaos.production-safety.allow-unsafe-defaults=true`; the authorization server has its own `chaos.authorization.production-safety.fail-fast` and `allow-*` switches.
