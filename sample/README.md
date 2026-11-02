# klite sample

This is a small sample application of how to use klite, which also uses the database.

Start with [Launcher](src/Launcher.kt)

## Development with hot reload

Run the app behind a disposable classloader:

```bash
./gradlew :sample:dev
```

In another terminal, keep compiling changed classes:

```bash
./gradlew -t :sample:classes
```

The `dev` task uses the dev-only [dev](../dev) module. It calls the static no-arg
`LauncherKt.createServer` factory in a disposable classloader, so even the first generation of
application classes can be unloaded when the build output changes. The module is not on the production
classpath: normal `main` calls the same `createServer` factory. Use `./gradlew :sample:run` for a normal,
non-reloading run.

## Tests

Running of klite tests also require starting a DB using (which is attempted to start automatically):

```docker compose up -d db```

This is generally much faster than using e.g. TestContainers, as you start DB once on your development machine
and use it for both the app and tests, which is really fast.

Some [klite-jdbc](../jdbc) tests are thus in this sample subproject.
