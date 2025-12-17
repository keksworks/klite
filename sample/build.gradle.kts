import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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
    "-o", project.file("build/types.ts"),
    "-p", "// Generated automatically by ./gradlew types.ts\n",
    "-t", "users.TestData"
  )
}

tasks.withType<KotlinCompile> {
  finalizedBy("types.ts")
}
