import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val mainClassName = "LauncherKt"
val devImplementation = configurations.create("devImplementation") {
  isCanBeConsumed = false
  isCanBeResolved = true
}

dependencies {
  implementation(project(":server"))
  implementation(project(":json"))
  implementation(project(":i18n"))
  implementation(project(":jdbc"))
  implementation(project(":slf4j"))
  implementation(project(":oauth"))
  implementation(project(":openapi"))
  implementation(libs.postgresql)
  add("devImplementation", project(":dev"))
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

/** Runs the app behind a disposable classloader from the dev-only :dev module */
tasks.register<JavaExec>("dev") {
  mainClass.set("klite.dev.HotReloadLauncherKt")
  classpath = sourceSets.main.get().runtimeClasspath + devImplementation
  args(mainClassName, "createServer")
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
