# Repository Guidelines

## Where the rules live

**[CLAUDE.md](CLAUDE.md) is the single source of truth** for this repository's structure, build commands,
architecture and coding conventions. Read it first. It covers:

- **Build commands** — unit tests, single-module builds, integration tests (`*IT.java`, needs Docker),
  coverage, release governance, structure governance, configuration-reference regeneration, archetype verification.
- **Versions and dependency management** — `${revision}` in `.mvn/maven.config`, `chaos-dependencies` as the
  single version source, the `optional` rule in `chaos-autoconfigure`, the configuration-processor requirement.
- **Architecture** — the module map (`chaos-foundation/`, `chaos-observability/`, `chaos-audit/`, `chaos-security/`,
  `chaos-gateway/`, `chaos-data/`, `chaos-mq/`, `chaos-storage/`, …), the ten dependency-direction rules enforced by
  `DependencyDirectionArchitectureTest` / `LayerBoundaryArchitectureTest`, the auto-configuration and starter pattern,
  configuration usability rules, archetypes and example services.
- **Conventions** — Java 21, UTF-8, 4-space indent, class naming by role, no `package-info.java`,
  no `spring.factories`, no `TODO` / `FIXME` / `System.out` / `printStackTrace`, production-safety behaviour.

This file used to restate all of the above in prose. Two copies of the same rules drift apart, and the drift is
invisible until someone follows the stale one — so it now covers only what is genuinely specific to contributing.

Deeper background lives in the docs: [architecture.md](chaos-docs/src/main/resources/docs/architecture.md)
(module responsibilities and dependency rules with rationale),
[docs/index.md](chaos-docs/src/main/resources/docs/index.md) (navigation),
and [optimization-roadmap.md](chaos-docs/src/main/resources/docs/optimization-roadmap.md) (open items).

## Testing Guidelines

Write JUnit Jupiter tests in the same package as the code under test. Unit tests are `*Test.java` in `src/test/java`;
integration tests are `*IT.java` in `src/integration-test/java` and run only under the `chaos-integration-test` profile.
Testcontainers may only be used from `*IT` classes.

Prefer a focused module build — `./mvnw -B -pl <module> -am test` — before running the full suite. Note that `-am`
matters: without it the module resolves its chaos dependencies from `~/.m2`, which may be stale.

Reuse the published fixtures in `chaos-test-support` (`@WithChaosContext`, `TestLoginUsers`, `ChaosMockMvcSecurity`,
`InMemoryRedisTemplates`, `ChaosContainers`, `ProductionSafetyTestSupport`) with a `test`-scope dependency instead of
copying helpers between modules.

When adding an architecture constraint, assert on structure rather than on source text. `LayerBoundaryArchitectureTest`
still contains many `Files.readString(...).contains(...)` assertions; they pass on a matching comment and prove nothing.
Prefer parsing the POM / imports, or writing a real behavioural test in the owning module.

## Commit & Pull Request Guidelines

Use clear, imperative commit subjects such as `Add gateway rate limit tests` or `Fix tenant context cleanup`.
Keep each commit scoped to one behaviour change.

Pull requests should state the problem, the approach, and the commands actually run with their results.
Link related issues. Add sample requests or screenshots for user-visible API or example-service behaviour changes.

Before opening a PR, run at least:

```bash
scripts/verify-structure.sh
./mvnw -B -Pchaos-coverage verify
python3 scripts/generate-configuration-reference.py --check   # after any @ConfigurationProperties change
```
