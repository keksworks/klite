# klite-dev

Dev-only module with development tooling, currently hot reloading application classes. It is not
needed in production and should only be added to a development classpath/configuration.

It starts the application through a disposable classloader, watches the application build output and,
after 300 ms without changes, stops the previous `Server`, drops the old classloader, and invokes the
application's server factory again in a fresh classloader. This means even the first generation of
application classes is disposable.

The application must expose a static, no-argument factory method that returns an unstarted
`klite.Server`:

```kotlin
fun main() {
  createServer().start()
}

fun createServer(): Server {
  // create and configure the server, but do not start it
}
```

Run the launcher with the application class and factory method:

```bash
java -cp ... klite.dev.HotReloadLauncherKt LauncherKt createServer
```

See the [sample dev task](../sample/build.gradle.kts) for a Gradle setup. In another terminal, keep
compiling changed classes, e.g.:

```bash
./gradlew -t :sample:classes
```
