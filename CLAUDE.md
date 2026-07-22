# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & run

This is a Maven project with the wrapper committed (`mvnw` / `mvnw.cmd`), so no local Maven install is required.

```bash
./mvnw clean package        # compile + run tests + package
./mvnw spring-boot:run      # run the application
./mvnw test                 # run all tests
./mvnw test -Dtest=CoresystembackendApplicationTests#contextLoads   # run a single test
```

Java 21 is required (`<java.version>21</java.version>` in `pom.xml`).

## Current state

This is a Spring Initializr-generated skeleton, not yet a feature-bearing app:

- Single entry point: `src/main/java/com/coresystem/coresystembackend/CoresystembackendApplication.java` (`@SpringBootApplication`, empty body).
- Config: `src/main/resources/application.yaml` only sets `spring.application.name`.
- Test: a single `@SpringBootTest contextLoads()` smoke test.
- Dependency so far is `spring-boot-starter` (not `-web`), so there is **no embedded servlet container / HTTP layer yet** — `spring-boot:run` will boot a context that exposes nothing. Adding `-web` (or another starter) is the expected next step when building endpoints.

## Spring Boot snapshot

`pom.xml` pins `spring-boot-starter-parent` to **`4.1.1-SNAPSHOT`** and enables the `spring-snapshots` repository (`repo.spring.io/snapshot`). Consequences:

- Builds resolve the latest snapshot of Spring Boot 4.x, not a fixed release. Behavior can shift between builds.
- Builds require network access to `repo.spring.io`; an offline build will fail to resolve the parent/BOM if the snapshot isn't cached locally.
- If reproducibility is needed, pin to a released version and remove the snapshot repository block.
