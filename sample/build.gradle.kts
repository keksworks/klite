import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("org.graalvm.buildtools.native") version "1.1.10"
}

val mainClassName = "LauncherKt"

dependencies {
  implementation(project(":server"))
  implementation(project(":json"))
  implementation(project(":i18n"))
  implementation(project(":jdbc"))
  implementation(project(":slf4j"))
  implementation(project(":oauth"))
  implementation(project(":openapi"))
  implementation(libs.postgresql)
  testImplementation(project(":jdbc-test"))
}

sourceSets {
  main {
    resources.srcDirs("db", "i18n")
  }
}

tasks.register<Copy>("deps") {
  into("$buildDir/libs/deps")
  from(configurations.runtimeClasspath)
}

tasks.jar {
  dependsOn("deps")
  doFirst {
    manifest {
      attributes(
        "Main-Class" to mainClassName,
        "Class-Path" to File("$buildDir/libs/deps").listFiles()!!.joinToString(" ") { "deps/${it.name}" }
      )
    }
  }
}

tasks.register<JavaExec>("run") {
  mainClass.set(mainClassName)
  classpath = sourceSets.main.get().runtimeClasspath
}

tasks.register<JavaExec>("types.ts") {
  dependsOn("testClasses")
  mainClass.set("klite.json.TSGenerator")
  classpath = sourceSets.test.get().runtimeClasspath
  args("${project.buildDir}/classes/kotlin/main",
    "-r", "Routes$",
    "-o", project.file("build/types.ts"),
    "-p", "// Generated automatically by ./gradlew types.ts\n",
    "-t", "klite.sample.users.TestData"
  )
}

tasks.withType<KotlinCompile> {
  finalizedBy("types.ts")
}

// GraalVM native image build: ./gradlew :sample:nativeCompile, run with build/native/nativeCompile/klite-sample
// klite relies heavily on Kotlin/Java reflection (annotated routes, JDBC row mapping, JSON (de)serialization),
// so any newly added reflectively-used classes must be captured by (re-)running the tracing agent:
//   ./gradlew -Pagent :sample:run   # exercise all the code paths you want covered, then Ctrl+C
//   ./gradlew :sample:metadataCopy  # merges the recorded config into src/META-INF/native-image
graalvmNative {
  metadataRepository { // use GraalVM's shared reachability metadata for 3rd-party deps, e.g. postgresql, HikariCP
    enabled.set(true)
  }
  agent {
    defaultMode.set("standard")
    metadataCopy {
      mergeWithExisting.set(true)
      inputTaskNames.add("run")
      outputDirectories.add("src/META-INF/native-image")
    }
  }
  binaries {
    named("main") {
      imageName.set("klite-sample")
      mainClass.set(mainClassName)
      buildArgs.add("--enable-http")
      buildArgs.add("--enable-https")
      buildArgs.add("-H:+ReportExceptionStackTraces")
    }
  }
}
