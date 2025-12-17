# Klite Tutorial

This tutorial will guide you through the process of creating a TODO REST API backend application using Kotlin & Klite, including a Postgres database.

As you will see, Klite is very small, but still provides turn-the-key features for modern web applications, more so than many other frameworks.

To get more information about any class or function, navigate freely inside to see how it works and what optional parameters it takes.

You can also use the [Klite + Svelte project template](https://github.com/keksworks/klite-starter) repo.

## Dependencies

Create a Kotlin Gradle project in IntelliJ IDEA, then add the Klite dependency to the `build.gradle.kts` file:

```kts
repositories {
  mavenCentral()
  maven { url = uri("https://jitpack.io") }
}

dependencies {
  fun klite(module: String) = "com.github.keksworks.klite:klite-$module:LASTEST_VERSION_HERE"
  implementation(klite("server"))
}
```

Fortunately, you won't get anything extra besides Klite itself, as it has no other dependencies, not even 3rd-party http server.

Note that it is recommended to download Klite sources for documentation and check them for any additional parameters/options. You may configure your IDE to download dependency sources automatically (in Intellij -> Settings -> Advanced Settings).

## Source sets

Gradle follows the Maven standard of having `src/main/kotlin` for production code and `src/test/kotlin` for tests by default.

However, it seems unnecessary deeply nested, so if you want to use a simpler `src`, `test`, and `db` instead, then use:

```kts
sourceSets {
  main {
    kotlin.srcDirs("src")
    resources.srcDirs("src", "db").exclude("**/*.kt")
  }
  test {
    kotlin.srcDirs("test")
    resources.srcDirs("test").exclude("**/*.kt")
  }
}
```

## Launcher

Good frameworks allow you to write your own main function, so that you are in control of the application lifecycle.
In Klite, you need to create an instance of [Server](server/src/klite/Server.kt):

```kotlin
import klite.Server

fun main() {
  Server().start()
}
```

This will start a server on the default port 8080.
You can change the listening IP and port by passing it as an argument to the `Server` constructor.
Passing `InetSocketAddress(0)` will bind to any available port.

You can also pass `Server(workerPool = Executors.newVirtualThreadPerTaskExecutor())` if you want to run request handlers in JVM virtual threads.

## Config

Most applications need some configuration. 12-factor applications use environment variables for configuration.
Klite provides a simple way to access environment variables using the [Config](core/src/Config.kt) object, and the Server already uses it to read the port such as `Config["PORT"]`, among other things.

In development, it makes sense to use an `.env` file to set environment variables, and also commit this file, so that fellow developers will get things running out of the box:

```kotlin
fun main() {
  Config.useEnvFile() // loads .env file if it exists and uses values from the file only if they are not set in the environment
  Server().start()
}
```

Klite also supports defining environment name with the ENV environment variable. Default values are `dev`, `test`, `prod`.

You may want to disable loading of `.env` file in production, and use environment variables only:

```kotlin
if (!Config.isProd) Config.useEnvFile()
```

Even if you don't add this `if`, Config still has precedence for environment variables over `.env` file.

## Routes

It's time to add our routes. Klite routes must be defined in a **context** (URL prefix with common meaning).

```kotlin
Server().apply {
  context("/api") {
    get("/hello") { "Hello, world!" }
  }
  start()
}
```

You can now get the plain text response from `http://localhost:8080/api/hello`.
This will also handle request logging for you.

## JSON

As is typical, we want our API to return JSON.

Klite uses registered [BodyRenderer](server/src/klite/Body.kt)s to render the response. By default, it uses `TextRenderer` to render plain text.

You can register more renderers to handle different content types.
Klite uses the `Accept` header to determine which content type the client wants.

Add [klite-json](json) dependency to your `build.gradle.kts`:

```kts
implementation(klite("json"))
```

Note: [klite-jackson](jackson) and [klite-serialization](serialization) are alternatives if you prefer more heavy-weight and harder to configure libraries.

Now you can register the `JsonBody` renderer and parser for the whole application:

```kotlin
  use<JsonBody>()
  context("/api") {
    useOnly<JsonBody>() // this removes other renderers in /api context
    get("/todos") { listOf(Todo("Buy groceries")) }
  }

  data class Todo(val item: String, val completedAt: Instant? = null)
```

Klite [JsonMapper](json/src/JsonMapper.kt) will omit nulls by default to make responses smaller.

You can now get the JSON response using `http://localhost:8080/api/todos`.

## Annotated routes

In real applications, it makes sense to divide routes into separate classes, which can also be unit-tested just like normal classes, independent of the framework.

Klite provides `klite.annotations` package with annotations to make it possible.

Let's create a `TodoRoutes`:

```kotlin
import klite.annotations.*

class TodoRoutes {
  @GET("/todos") fun todos() = listOf(Todo("Buy groceries"))
}
```

Then, you can register this class in the /api context:

```kotlin
  context("/api") {
    annotated<TodoRoutes>("/todos")
  }
```

This will add all annotated methods from `TodoRoutes` to the context as route handlers.
In some other frameworks, these are called "Controller" or "Resource". You are free to use any name in Klite.

## Dependency injection

Why not use `annotated(TodoRoutes())`?
Because you may want Klite to create the instance and inject any dependencies into it that it declares as constructor parameters.

Let's create a basic in-memory repository for storing of our TODOs:

```kotlin
class TodoRepository {
  private val todos = mutableListOf<Todo>()

  fun list() = todos.toList()
  fun save(todo: Todo) = todos.add(todo)
}
```

Now, we can inject this repository into our `TodoRoutes`:

```kotlin
class TodoRoutes(private val repo: TodoRepository) {
  @GET fun todos() = repo.list()
  @POST fun save(todo: Todo) = repo.save(todo)
}
```

`JsonBody` will deserialize Todo instance from JSON request automatically.

Klite uses `Server.registry` for dependency injection.
[Registry](core/src/Registry.kt) will create singleton classes recursively by default.

If you need to register instances of interfaces, you can use the `register()` function.

## Path parameters

Let's add a route to get a single TODO.

First, our Todo class should have an id field:

```kotlin
typealias Id<T> = TSID<T>
data class Todo(val item: String, val completedAt: Instant? = null, val id: Id<Todo> = Id())
```

We use [TSID](core/src/TSID.kt) to generate unique and type-safe IDs for our TODOs, let's have it auto-generate when a new Todo is posted. Alternatively, you can use UUID or any type that you like.

Let's add a corresponding method to the TodoRepository:

```kotlin
  fun get(id: Id<Todo>) = todos.first { it.id == id }
```

Now, we can add a route to get a single TODO by its id:

```kotlin
  @GET("/:id") fun byId(@PathParam id: Id<Todo>) = repo.get(id)
```

## Database

In real applications, you would use a database to store your data.

Let's spin-up a Postgres database using Docker.

Create the following `docker-compose.yml` file:

```yml
service:
  db:
    image: postgres:alpine
    environment:
      POSTGRES_USER: todo
      POSTGRES_PASSWORD: todo
    ports:
      - "5432:5432"
```

Then add [klite-jdbc](jdbc) dependency to your `build.gradle.kts`:

```kts
implementation(klite("jdbc"))
```

Now we can auto-start the DB and connect to it in our Launcher:

```kotlin
Server().apply {
  if (Config.isDev) startDevDB()
  use<DBModule>()
  ...
}
```

DBModule uses [PooledDataSource](jdbc/src/PooledDataSource.kt), wrapping [ConfigDataSource](jdbc/src/ConfigDataSource.kt) by default.

For the connection to succeed, you need to add the following to your `.env` file:

```env
DB_URL=jdbc:postgresql://localhost:5432/todo
DB_USER=todo
DB_PASS=todo
```

Now, we can reimplement TodoRepository to use the `todos` table in the DB:

```kotlin
import klite.jdbc.*

class TodoRepository(private val db: DataSource) {
  fun list() = db.select<Todo>("todos")
  fun save(todo: Todo) = db.upsert("todos", todo.toValues())
  fun get(id: Id<Todo>) = db.select<Todo>(Todo::id to id).first()
}
```

See [klite-jdbc](jdbc) docs for more examples of how to use it.

## DB migrator

If you ran the previous code, you probably have realized that the `todos` table does not exist in the database yet.

Let's use [DBMigrator](jdbc/src/migrator/DBMigrator.kt) to create the table for us:

```kotlin
use<DBModule>()
use<DBMigrator>()
```

DBMigrator will look for `db.sql` file in the root of the classpath (resources, use the `db` directory if you have redefined Gradle source sets above) and run it against the database, let's create it:

```sql
--changeset todos
create table todos (
  id bigint primary key,
  item text not null,
  completedAt timestamptz
);

--changeset todos:initial-data context:dev
insert into todos (id, item) values (123, 'Buy groceries');
```

Note that we use matching column names to the class properties, so that we can use `toValues()` method to insert the whole object. That avoids unnecessary remapping of names. If you need to use a different column name somewhere, you can use `@Column` annotation on a property.

Later you can add more changesets to the same file, and DBMigrator will run only new ones, or even extract them to separate files and use `--include file.sql`. See [ChangeSet](jdbc/src/migrator/ChangeSet.kt) for more attributes.

## CrudRepository

To make it even easier to implement repositories, [klite-jdbc](jdbc) includes BaseCrudRepository:

```kotlin
data class Todo(...): BaseEntity<Id<Todo>>
class TodoRepository(db: DataSource): BaseCrudRepository<Todo, Id<Todo>>(db, "todos")
```

And then you will have the common `list()`, `get()`, `save()` methods already implemented for you.

## Transactions

By default, JDBC Connections work in autoCommit mode, which means that every statement is a separate transaction.

In real life it makes sense to use a **transaction per-request** model, which will issue a rollback automatically in case anything fails with an exception.

Add `use<RequestTransactionHandler>()`, which will do it for you.

Then, `@NoTransaction` can be used to disable transaction for a specific route.

## Error handling

Klite makes it easy to handle errors without extra code by default.

You can register custom exception types to produce specific HTTP error responses:

```kotlin
errors.on<IllegalAccessException>(StatusCode.Forbidden)
```

In fact, many Klite modules add their own error handlers, e.g. Kotlin built-in functions `require()` and `error()` will produce BadRequest 400 responses.

## Converter & Validation

Data validation is also already done by default, i.e. Kotlin nullability is respected and reported to the user automatically.

The most convenient for additional validation is to use correct data types (e.g. Email, Phone, URL, not just String).

With Klite, it's easy to add custom value types that would work across request parameters, JSON and database columns.
In fact, Klite already provides common [Email, Phone, Password types](core/src/Types.kt).

Any type with String constructor or String static factory method will be supported by default.

```kotlin
class EstonianPersonalCode(value: String): StringValue(value) {
  init {
    require(value.length == 11) { "EE personal code should be 11 characters long" }
  }
}
```

Custom type creation can be registered with [Converter](core/src/Converter.kt):

```kotlin
Converter.use { Locale.forLanguageTag(it.replace('_', '-')) }
```

If you need additional validation, then add require calls to entity constructor:

```kotlin
data class Todo(...) {
  init {
    require(item[0].isUpperCase()) { "Item should start with upper case" }
  }
}
```

Then no extra validation code is needed in route handlers, which is easy to forget.

`JsonMapper.trimToNull` is enabled by default, so you get more clean data into your objects, and won't get empty strings into required (non-null) fields — input Strings will be trimmed and converted to null if empty.

## Metrics

You can add the built-in `metrics()` handler outside of other contexts to expose important health metrics there that your monitoring tools can read and log, e.g. Prometheus.

Use the `Metrics.register()` function to register custom metrics that you want to track.

## Swagger / OpenAPI

Add the optional module [openapi](openapi) to your `build.gradle.kts`:

```kts
implementation(klite("openapi"))
```

And then add `openApi()` handler to your API context:

```kotlin
context("/api") {
  // your routes go first
  openApi()
}
```

This will expose `/api/openapi` endpoint with the OpenAPI 3.0 spec for all routes in the context, viewable with Swagger UI.

Use Swagger annotations to customize the generated spec or hide any routes with `@Hidden`.

## Login/access/sessions

To enable session support, pass a `SessionStore` implementation to Server's constructor:

```kotlin
Server(sessionStore = CookieSessionStore())
```

`CookieSessionStore` will store session data in a cookie, which survives server restarts and works with multiple server instances. Session data is encrypted and cannot be tampered with, but can be subject to replay attacks if you rely on changes in the session state for security checks.

Store as few data as possible in the session and use it mostly for user identification and authorization.
Any state-related data in session will be problematic with the browser's back button.

Then you can use `session` object to store and retrieve session data, e.g:

```kotlin
@POST fun login(credentials: Credentials) {
  val user = userRepository.by(credentials) ?: throw UnauthorizedException()
  session["userId"] = user.id.toString()
}

data class Credentials(val username: String, val password: Password)
```

and then have a before handler to load the current user:

```kotlin
context("/api") {
  ...
  before {
    val userId = it.session["userId"] ?: throw ForbiddenException()
    it.attr("user", userRepository.get(userId))
  }
}
```

`attr()` values can be accessed in annotated routes with `@AttrParam` annotation.

`before, after, decorate` handlers can be used to add common logic to all routes in a context.
They affect only the routes that follow them.

## HTML

If you want to render HTML server-side, you can implement your own `BodyRenderer` using your favorite template engine or just use Kotlin template strings for HTML generation.

```kotlin
  context("/html") {
    get("/:name") {
      send(OK, """
        <h1>Hello, ${+path("name")}!</h1>
      """, MimeTypes.html)
    }
  }
```

You can now get the HTML response using `http://localhost:8080/html/klite`.

Note the `+` before `path("name")`. This is a special operator to escape HTML characters in the string, import it from `klite.html.unaryPlus`.

## Assets / Static files

Instead, modern applications would use a frontend framework, such as Svelte or React, and serve the frontend as a static site.

Klite has a built-in static file server, which you can use to serve your frontend files.

```kotlin
  assets("/", AssetsHandler(Path.of("public"), useIndexForUnknownPaths = true))
```

This adds its own context; it is a good idea to use `/` as the path, so that files are available from shorter URLs.

The latter parameter is useful for SPA (single-page applications), where the frontend router handles the URL.
