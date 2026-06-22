dependencies {
  api(project(":core"))
  compileOnly(project(":server"))
  testImplementation(project(":server"))
  compileOnly(project(":json"))
  compileOnly(libs.postgresql)
  testImplementation(libs.postgresql)
  compileOnly(libs.hikari) {
    exclude("org.slf4j")
  }
}
