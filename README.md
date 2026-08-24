# <img src="logo.png" alt="Klite" width=128 height=128>

[![Release](https://jitpack.io/v/keksworks/klite.svg)](https://jitpack.io/#keksworks/klite) [![Build & Test](https://github.com/keksworks/klite/actions/workflows/ci.yml/badge.svg)](https://github.com/keksworks/klite/actions/workflows/ci.yml)

# Klite

**A tiny, non-blocking Kotlin/JVM web framework with batteries included.**

Klite gives you a fast path from an idea to a production service: write plain Kotlin, start a server, and add only the modules you need. It is inspired by SparkJava and Jooby, but is [smaller, simpler, and better](docs/Comparisons.md).

Klite is built for developers—and for the AI-assisted development era. Its behavior is explicit, its source is short and readable, and your application code stays mostly independent of the framework. No sprawling abstractions or annotation magic for you or your coding agent to decipher.

> **Sustainable by default:** low resource usage means low infrastructure costs and lower CO₂ emissions.

Please **star the repo** if Klite looks useful to you.

## See it in action

```kotlin
fun main() {
  Server().apply {
    assets("/", AssetsHandler(Path.of("public"), useIndexForUnknownPaths = true))
    context("/api") {
      get("/hello") { "Hello, world!" }
      // Or use a plain annotated route class:
      annotated<MyRoutesClass>("/my")
    }
    start()
  }
}
```

Read the [Tutorial](TUTORIAL.md) for a guided TODO REST API, or explore the [sample project](sample), which includes a database, Docker setup, SSE, OpenAPI, and OAuth.

## Why developers choose Klite

- **Zero dependencies:** the core server uses Java's built-in, non-blocking `jdk.httpserver`; no third-party server is hidden underneath.
- **Minimal ceremony:** clear Kotlin APIs, automatic validation and exception handling, and a small, debuggable codebase.
- **No magic:** behavior is explicit, overridable, and easy to test.
- **Kotlin-first type safety:** modern Kotlin features and class types work naturally, including shared value classes such as `Id<User>`, `Phone`, and `Email` across HTTP, JDBC, JSON, and XML.
- **Coroutines that work:** proper coroutine support, including reliable before/after filters for transactions, logging, and other cross-cutting concerns.
- **Two routing styles:** a concise route builder DSL or plain annotated classes.
- **Built-in DI:** constructor-based dependency injection for singletons.
- **Production essentials:** logging, JSON, XML, CSV, JDBC, migrations, jobs, i18n, OpenAPI, OAuth, SMTP, push notifications, and AI integrations.
- **Small deployments:** a jlink-built [sample Docker image](sample/Dockerfile) is about 50–70 MB, depending on modules; production apps can run with as little as 50 MB of heap.

## Modules

Use the pieces independently where useful:

| Module | Purpose |
| --- | --- |
| [server](server) | Main HTTP server; zero external dependencies |
| [json](json) | Lightweight, configurable JSON parsing/rendering and TypeScript type generation |
| [xml](xml) | Fast XML parsing into data classes |
| [csv](csv) | Simple CSV parsing and generation |
| [jdbc](jdbc) | JDBC extensions, transactions, database access, and migrations |
| [jdbc-test](jdbc-test) | Test database code against a real database |
| [jobs](jobs) | Scheduled `JobRunner` |
| [i18n](i18n) | Server-side translations |
| [openapi](openapi) | OpenAPI 3.0 generation for routes; view with [Swagger UI](https://swagger.io/tools/swagger-ui/) |
| [oauth](oauth) | OAuth 2.0 login with several providers |
| [smtp](smtp) | SMTP email sending |
| [push](push) | Browser Web Push notifications with VAPID |
| [ai](ai) | AI clients, MCP servers, and PDF data extraction |

The reusable [core](core) module contains shared classes such as `Config` and is normally used transitively.

### Integrations

These optional modules connect Klite to external libraries:

- [slf4j](slf4j) — redirect server logs to SLF4J (recommended for production)
- [jackson](jackson) — JSON through Jackson
- [serialization](serialization) — JSON through kotlinx-serialization
- [liquibase](liquibase) — Liquibase database migrations

## Install

Klite requires **Java 21 or newer**. `jdk.httpserver` has been part of the JDK since Java 6, and Java 9+ provides re-routable `System.Logger`.

Add the modules you need through [JitPack](https://jitpack.io/#keksworks/klite):

```kotlin
repositories {
  mavenCentral()
  maven { url = uri("https://jitpack.io") }
}

dependencies {
  val kliteVersion = "main-SNAPSHOT" // Prefer a released tag or commit hash
  fun klite(module: String) = "com.github.keksworks.klite:klite-$module:$kliteVersion"

  implementation(klite("server"))
  implementation(klite("json"))
  implementation(klite("jdbc"))
  testImplementation(klite("jdbc-test"))
}
```

Configure your IDE to download dependency sources (IntelliJ: **Settings → Advanced Settings**). The source is intentionally part of the documentation.

### Fork, local, or source builds

JitPack builds requested versions on demand, so you can use unreleased commits or a customized fork as a normal Gradle dependency. Pull requests are welcome.

For a local build, publish to `~/.m2/repository`:

```bash
./gradlew publishToMavenLocal
```

Then add `mavenLocal()` and use `main-SNAPSHOT`.

If JitPack is unavailable, depend directly on the Git repository from `settings.gradle.kts`:

```kotlin
sourceControl {
  gitRepository(java.net.URI("https://github.com/keksworks/klite.git")) {
    producesModule("com.github.keksworks.klite:server")
    producesModule("com.github.keksworks.klite:jdbc")
    // Add every subproject used by build.gradle.kts, without the "klite-" prefix.
  }
}
```

Gradle clones and builds Klite automatically. Tagged versions work this way; commit hashes do not.

## Design principles

- Follow [The Pure Code Manifesto](https://github.com/keksworks/manifesto).
- Prefer the smallest amount of code: maintainability and easy change come before theoretical performance gains.
- Support 12-factor applications by default.
- Make it easy to add another server implementation if needed.
- Keep most application code framework-independent, so switching remains practical.
- Offer both route builders and annotated classes.
- Keep documentation lightweight: the source is short enough to read directly.

## Performance

Klite, including `jdk.httpserver`, adds **under 1 ms of overhead per request after warmup**. On an Ubuntu laptop with Java 25 and a 2020 i7-1165G7:

- A simple route handled about **23,000 requests/second**, with 99% under 1 ms:
  `ab -n 10000 -c 10 http://localhost:8080/api/hello`
- A JDBC route handled about **8,000 requests/second**, with 99% under 1 ms:
  `ab -n 10000 -c 10 http://localhost:8080/api/hello/user/9725b054-426b-11ee-92a5-0bd2a151eea2`
- With 1,000 concurrent requests, coroutine suspension handled about **8,000 requests/second**, with 80% completing within a 100 ms delay:
  `ab -n 10000 -c 1000 http://localhost:8080/api/hello/suspend`

Run these benchmarks with the [sample project](sample).

## In production

Klite's main server module is only about **1,000 lines of code** and powers dozens of known production applications. It was publicly announced at [KKON 2022](https://rheinwerk-kkon.de/programm/keks-klite/); see [the slides](https://docs.google.com/presentation/d/1m5UORE88nVRdZXyDEoj74c0alk1Ff_tX8mfB8oLMbk0).

Open-source applications built with Klite include:

- [StoryTracker](https://github.com/keksworks/storytracker) — agile project management
- [AitaValida](https://github.com/keksworks/aitavalida) — voting compass for Estonian elections

For an AI-readable overview, see [llms.txt](llms.txt). See [CHANGELOG.md](CHANGELOG.md) for releases and changes.
