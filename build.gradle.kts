import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm") version libs.versions.kotlin
}

allprojects {
  group = "io.github.keksworks.klite"
  version = project.findProperty("version") ?: "main-SNAPSHOT" // see tags/releases
}

subprojects {
  apply(plugin = "kotlin")
  apply(plugin = "maven-publish")
  apply(plugin = "signing")

  repositories {
    mavenCentral()
  }

  dependencies {
    val libs = rootProject.libs
    testImplementation(libs.junit)
    testRuntimeOnly(libs.junit.engine)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.atrium) {
      exclude("org.jetbrains.kotlin")
    }
    testImplementation(libs.mockk) {
      exclude("org.jetbrains.kotlin")
    }
    testImplementation(libs.kotlinx.coroutines.test)
  }

  sourceSets {
    main {
      java.setSrcDirs(emptyList<String>())
      kotlin.setSrcDirs(listOf("src"))
      resources.setSrcDirs(listOf("src")).exclude("**/*.kt")
    }
    test {
      java.setSrcDirs(emptyList<String>())
      kotlin.setSrcDirs(listOf("test"))
      resources.setSrcDirs(listOf("test")).exclude("**/*.kt")
    }
  }

  java.sourceCompatibility = JavaVersion.VERSION_21
  java {
    toolchain {
      languageVersion = JavaLanguageVersion.of(21)
    }
  }

  kotlin {
    jvmToolchain(21)
  }

  tasks.withType<KotlinCompile> {
    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_21)
      freeCompilerArgs.addAll(
        "-Xreturn-value-checker=check",
        "-Xcollection-literals",
        "-Xindy-allow-annotated-lambdas=false",
        "-opt-in=kotlin.ExperimentalStdlibApi",
        "-opt-in=kotlin.time.ExperimentalTime",
      )
    }
  }

  tasks.jar {
    archiveBaseName.set("${rootProject.name}-${project.name}")
    manifest {
      attributes(mapOf(
        "Implementation-Title" to archiveBaseName,
        "Implementation-Version" to project.version
      ))
    }
  }

  java {
    withSourcesJar()
    withJavadocJar()
  }

  tasks.named<Jar>("sourcesJar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
  }

  tasks.test {
    useJUnitPlatform()
    javaLauncher.set(javaToolchains.launcherFor {
      languageVersion = JavaLanguageVersion.of(25)
    })
    jvmArgs("--enable-preview", "--add-opens=java.base/java.lang=ALL-UNNAMED")
  }

  // disable publishing gradle .modules files as JitPack omits excludes from there: https://github.com/jitpack/jitpack.io/issues/5349
  tasks.withType<GenerateModuleMetadata> {
    enabled = false
  }

  configure<PublishingExtension> {
    publications {
      if (project.name != "sample") {
        register<MavenPublication>("maven") {
          from(components["java"])
          afterEvaluate {
            artifactId = tasks.jar.get().archiveBaseName.get()
          }
        }
      }
    }
  }
}
