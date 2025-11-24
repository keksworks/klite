dependencies {
  implementation(project(":server"))
  api("io.swagger.core.v3:swagger-annotations:2.2.41")

  testImplementation(project(":json"))
  testImplementation(project(":jackson"))
}
