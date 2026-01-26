rootProject.name = "klite"

dependencyResolutionManagement {
  versionCatalogs {
    create("libs") {
      version("kotlin", "2.3.0")

      val coroutines = version("coroutines", "1.9.0")
      library("kotlinx-coroutines", "org.jetbrains.kotlinx", "kotlinx-coroutines-jdk8").versionRef(coroutines)
      library("kotlinx-coroutines-test", "org.jetbrains.kotlinx", "kotlinx-coroutines-test").versionRef(coroutines)
      library("kotlinx-serialization-json", "org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
      library("kotlinx-datetime", "org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")

      val junit5 = version("junit", "5.13.4")
      library("junit", "org.junit.jupiter", "junit-jupiter").versionRef(junit5)
      library("junit-api", "org.junit.jupiter", "junit-jupiter-api").versionRef(junit5)
      library("junit-engine", "org.junit.jupiter", "junit-jupiter-engine").versionRef(junit5)
      library("junit-launcher", "org.junit.platform:junit-platform-launcher:1.13.4")
      library("atrium", "ch.tutteli.atrium:atrium-fluent:1.3.0-alpha-2")

      library("mockk", "io.mockk:mockk:1.14.7")
      // TODO: remove when mockk upgrades to support Java 25: https://github.com/mockk/mockk/issues/1434
      val byteBuddy = version("byte-buddy", "1.17.8")
      library("byte-buddy", "net.bytebuddy", "byte-buddy").versionRef(byteBuddy)
      library("byte-buddy-agent", "net.bytebuddy", "byte-buddy-agent").versionRef(byteBuddy)

      val slf4j = version("slf4j", "2.0.17")
      library("slf4j-api", "org.slf4j", "slf4j-api").versionRef(slf4j)
      library("slf4j-jul", "org.slf4j", "jul-to-slf4j").versionRef(slf4j)

      val jackson = version("jackson", "2.19.1")
      library("jackson-jsr310", "com.fasterxml.jackson.datatype", "jackson-datatype-jsr310").versionRef(jackson)
      library("jackson-kotlin", "com.fasterxml.jackson.module", "jackson-module-kotlin").versionRef(jackson)

      library("hikari", "com.zaxxer:HikariCP:7.0.2")
      library("liquibase-core", "org.liquibase:liquibase-core:5.0.1")
      library("postgresql", "org.postgresql:postgresql:42.7.9")
    }
  }
}

include(
  "core",
  "server",
  "json",
  "csv",
  "xml",
  "jackson",
  "i18n",
  "serialization",
  "jdbc",
  "jobs",
  "jdbc-test",
  "oauth",
  "smtp",
  "liquibase",
  "slf4j",
  "openapi",
  "sample"
)
