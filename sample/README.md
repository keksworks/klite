# klite sample

This is a small sample application of how to use klite, which also uses the database.

Start with [Launcher](src/Launcher.kt)

## Tests

Running of klite tests also require starting a DB using (which is attempted to start automatically):

```docker compose up -d db```

This is generally much faster than using e.g. TestContainers, as you start DB once on your development machine
and use it for both the app and tests, which is really fast.

Some [klite-jdbc](../jdbc) tests are thus in this sample subproject.

## Native image (GraalVM)

This subproject can be compiled into a standalone [GraalVM](https://www.graalvm.org) native executable using the
[GraalVM Native Build Tools](https://graalvm.github.io/native-build-tools/) Gradle plugin:

```shell
./gradlew :sample:nativeCompile   # produces build/native/nativeCompile/klite-sample
./gradlew :sample:nativeRun       # builds (if needed) and runs the native executable
```

This requires a GraalVM distribution (e.g. installed via [sdkman](https://sdkman.io): `sdk install java 25-graalce`)
to be used as the Gradle toolchain/JAVA_HOME when running these tasks.

klite relies heavily on Kotlin/Java reflection (e.g. `annotated<Routes>()` route discovery, JDBC row-to-object
mapping, JSON (de)serialization), which native-image cannot see statically. Reflection metadata for known
3rd-party dependencies (postgresql, HikariCP, etc.) is supplied automatically via GraalVM's shared
[Reachability Metadata Repository](https://www.graalvm.org/latest/reference-manual/native-image/metadata/#reachability-metadata-repository).

For klite's own reflectively-used classes (routes, entities, DTOs), regenerate the metadata whenever you add new
ones, using the [tracing agent](https://www.graalvm.org/latest/reference-manual/native-image/metadata/AutomaticMetadataCollection/):

```shell
./gradlew -Pagent :sample:run       # exercise the app/endpoints you want covered, then stop it
./gradlew :sample:metadataCopy      # merges the recorded config into src/META-INF/native-image
```
