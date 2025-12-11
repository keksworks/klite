# klite-server

This is the main component of Klite - the [Server](src/klite/Server.kt).

Create an instance, overriding any defaults using named constructor parameters, add contexts and routes, then start.

Basic usage:
```kotlin
  Server().apply {
    context("/api") {
      // Lambda routes
      get("/hello") { "Hello World" }
      post("/hello") { "You posted: $rawBody" }

      // or take routes from annotated functions of a class (better for unit tests)
      annotated<MyRoutes>()
    }

    start()
  }
```
See the [sample subproject](../sample/src/Launcher.kt) for a full working example.

Route handlers run in context of [HttpExchange](src/klite/HttpExchange.kt) and can use its methods to work with
request and response.

Anything returned from a handler will be passed to [BodyRenderer](src/klite/Body.kt) to output the response with correct Content-Type. BodyRenderer is chosen based on the Accept request header or first one if no matches.

POST/PUT requests with body will be parsed using one of registered [BodyParsers](src/klite/Body.kt) according to the request Content-Type header.
The following body parsers are enabled by default:
* `text/plain` - [TextBodyParser](src/klite/Body.kt)
* `application/x-www-form-urlencoded` - [FormUrlEncodedParser](src/klite/Body.kt)
* `multipart/form-data` - [MultipartParser](src/klite/MultipartParser.kt)

use<[JsonBody](../json/src/JsonBody.kt)>() for `application/json` support.

## Converter

[Converter](../core/src/Converter.kt) is used everywhere to convert incoming strings to the respective (value) types, e.g. request parameters, json fields, database values, etc.

This allows you to bind types like `LocalDate` or `UUID` directly in your routes, as well as `Converter.use` any custom
types very easily, like `Email`, `PhoneNumber`, etc.

## Contexts

All routes must be organized into contexts with path prefixes. A context with the longest matching path prefix is chosen for handling a request.

## Assets (static content)

A simple [AssetsHandler](src/klite/AssetsHandler.kt) is provided to serve static files.

```kotlin
assets("/", AssetsHandler(Path.of("public")))
```

For SPA client-side routing support, create AssetsHandler with `useIndexForUnknownPaths = true`.
*Warning:* this won't return 404 responses for missing paths anymore, but will render the index file.

## Config

[Config](../core/src/Config.kt) object is provided for an easy way to read System properties or env vars.

Use `Config.fromEnvFile()` if you want to load default config from an `.env`. This is useful for local development.

## Registry (and Dependency Injection)

[Registry](../core/src/Registry.kt) and it's default implementation - `DependencyInjectingRegistry` - provide
a simple way to register and require both Klite components and repositories/services of your application.

`DependencyInjectingRegistry` is used by default and can create any classes by recursively creating their constructor
arguments (dependencies).

You can use `register<MyInterface>(MyImplementation::class)` or `register<MyImplementation>()` to register a specific implementation that needs to be used for an interface. Otherwise, calling `require<MyClass>()` will try to auto-create MyClass and all its dependencies, if any.

See [it's tests](test/klite/DependencyInjectingRegistryTest.kt) for usage examples.

## Decorators

You can add both global and context-specific [decorators](src/klite/Decorators.kt), including `Before` and `After` handlers.
The order is important, and decorators apply to all *following routes* that are defined in the same context.

E.g. you can use the built-in [CorsHandler](src/klite/CorsHandler.kt).

## Error handling

Any exception thrown out of route handler will be passed to [ErrorHandler](src/klite/ErrorHandler.kt) to produce a response.
The [ErrorResponse](src/klite/ErrorHandler.kt) is then passed to [BodyRenderer](src/klite/Body.kt), like normal responses.

```kotlin
  Server().apply {
    errors.on<MyException>(BadRequest)
    errors.on<OtherException> {
      // some logic
      ErrorResponse(BadRequest, "custom message")
    }
  }
```

## Sessions

Session support can be enabled by providing a [SessionStore](src/klite/Session.kt) implementation, e.g.
```kotlin
  Server(sessionStore = CookieSessionStore())
```

The included [CookieSessionStore](src/klite/Session.kt) stores sessions in an encrypted cookie, which doesn't require any synchronization between multiple server nodes. It requires a `Config["SESSION_SECRET"]` to be available to derive an encryption key. Make sure it is different in all your environments.

You can implement your own store if you want sessions to be stored in e.g. a database.

## (SSE) Server-Sent Events

Supported using coroutines. Use [`exchange.startEventStream()`](src/klite/sse/Event.kt) and then `exchange.send()` in a loop.
On the client-side, use browser's built-in `EventSource` class that will do reconnects automatically.
See usage [sample](../sample/src/SSERoutes.kt).

This is a much lighter alternative to WebSockets, based on HTTP, not a separate protocol.

## HTML templates for server-side rendering

No built-in support for that. You may either implement a [BodyRenderer](src/klite/Body.kt) that will pass route responses to your favorite template engine or just call the engine in your routes and produce html output directly with `send(OK, "html", "text/html")`.

In Kotlin, you may also consider using template strings for html/xml generation, see the provided [helpers](src/klite/html/Helpers.kt):
```kotlin
get("/hello") {
  """<html><body><h1>Hello ${+query("who")}</h1></body></html>"""
}
```

The latter will be even better once [string template processors](https://youtrack.jetbrains.com/issue/KT-64632/Support-Java-21-StringTemplate.Processor) become available in Kotlin.

## Running behind a https proxy / load balancer

In most production environments your app will be running behind a load balancer and https proxy.
Proxies will forward some standard headers, that your app will need to understand:

* To support `X-Request-Id` header (e.g. in Heroku), pass [*XRequestIdGenerator*](src/klite/RequestIdGenerator.kt) instance to the Server
* To support `X-Forwarded-For` and the like, pass [*XForwardedHttpExchange*](src/klite/XForwardedHttpExchange.kt) constructor to the Server

```kotlin
  Server(requestIdGenerator = XRequestIdGenerator(), httpExchangeCreator = XForwardedHttpExchange::class.primaryConstructor!!)
```

Enable these only if you are sure that you will be running behind a trusted proxy in production.

## Optional features you can enable

* `enforceHttps()` - will redirect all http requests to https.
* `enforceCanonicalHost(name)` - will redirect all requests to a provided canonical host name.
* `useHashCodeAsETag()` - will add ETag headers based on response body hash code and respond with 304 Not Modified when applicable.
* `metrics()` - adds a Prometheus-compatible metrics endpoint at `/metrics`.
* [openApi()](../openapi) - will generate a Swagger/OpenAPI 3.0 spec for all routes in a context.

## Best practices

* Organize your code into domain packages, e.g. `payments`, `accounting`, and not by type of class, e.g. `controllers`, `repostories`, `services`, etc.
* Route handler's job is to parse the request, call a service method and transform/return the result. It should not implement business logic itself.
* Prefer annotated route handlers for easier code separation and unit testing.
* Do not catch common exceptions in your route handlers, but use `ErrorHandler.on` to add a global error handler instead based on Exception type, reducing code duplication.
* Store only minimal state in a session, e.g. authenticated user id. Everything else should be part of the UI flow and support back/forward buttons.
