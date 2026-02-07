# Unreleased
* server: map FileNotFoundException and NoSuchFileException to 404 status code by default
* core: TSIDGenerator.generateValue() can now accept desired timestamp instead of current one
* jdbc: `PostgresNotifier.consumeNotifications()` will now reconnect automatically if connection is lost
* jdbc: `PooledConnection.longUsed` can now be used to mark connections not to trigger `leakChecker`
* i18n: setting of Lang.jsonFiles can be used to load additional language files, e.g. for server-side-only translations

# 1.8.0
* server: StatusCodeExceptions will now contain stack trace in dev mode
* server: HttpExchange.attr/attrPut can be used for class-based attributes
* server: introduce `rateLimit()` decorator
* server: support for UTF-8 fileName (in Content-Disposition header)
* jdbc: Transaction now supports multiple DataSources (one connection per each)
* jdbc: Postgres: unwrap `PGobject` values automatically for non-standard types like `citext`
* json: Sequence can now be rendered to json directly (as an array)

# 1.7.3
* openapi: fix detection of default (undefined) schema implementation
* openapi: better support for annotation instances created directly in Kotlin code
* server/openapi: fix analyzing of class-decorated handlers for OpenAPI generation
* core/openapi: fix initialization of Converter-supported types in OpenAPI generation
* json: deprecate KeyConverter and ValueConverter in favor of the ones in `klite-core` module
* server: basicAuth() helper
* jdbc: `DockerCompose.startDB()` will now wait until DB actually accepts connections
* jdbc: support kotlin.uuid.Uuid and kotlin.time.Instant by default

# 1.7.2
* server: HttpServer now accepts connections inside of workerPool, not single thread - for better scalability under very heavy load
* server: `start()` method now accepts socketBacklog parameter that can be fine-tuned if you have 1000s of parallel connections
* server/jobs: ensure that JobRunner logs with the same instanceId as the Server (they use the same RequestIdGenerator now)
* server: `metrics()` can be provided with custom access annotations and includes `requestsActive` metric now
* server: `metrics()` can output Prometheus/OpenMetrics format if `Accept` header requests it
* jdbc: `metrics()` for PooledDataSource connection pool include `"count"` and `"dropped"` metrics now
* jdbc: changesets now support providing multiple comma-separated contexts
* jdbc: PooledDataSource now logs number of connections in the pool when dropping previous ones
* jdbc: BaseCrudRepository.get(forUpdate = true) now uses "for no key update" for Postgres to avoid unnecessary child rows locking

# 1.7.1
* GitHub organization changed to keksworks - update your dependency organization
* jdbc: PooledDataSource.queryTimeout can be configured separately from pool connection timeout
* server: MultipartParser and MultipartRenderer now support binary data/files
* server: nullable List of @QueryParam will now get null value if parameter is missing, not empty list
* server: introduced Metrics for publishing of app metrics, and a metrics() handler
* server: RequestTransactionHandler now supports exclude methods, to skip transactions for e.g. GET/HEAD requests
* core: new extensions for easier working with Java HttpClient, like get()/post()/put()
* xml: new module for fast and lightweight XML parsing
* Java 25 support

# 1.7.0
* core: TSIDGenerator can now be used with custom classes outside TSID companion
* core/jdbc: Any.toValues()/BaseCrudRepository.persister() will now return KProperty1 keys, not Strings for added type-safety.
  - If you need to use String keys, use `toValues().mapKeys { it.key.name }`
  - You may get "column specified more than once" errors if you have code like `entity.toValues() + ("hello" to 123)`, replace `"hello"` with `Entity::hello` in that case
  - It is now recommended to use `toValues()` for both providing values and skipping them, e.g. `entity.toValues(Entity::field1 = "other value, skip = setOf(Entity::field2))` - this avoids creation of intermediate maps and is faster
  - PropValue type alias now accepts 2 type arguments, so replace usages of `PropValue<T>` with `PropValue<T, *>`
* json: `toJsonValues()` introduced for the cases when you want to modify how an entity is serialized into json (e.g. remove some keys)
* jdbc: introduce @Column annotation to override DB column names for entity fields, previous internal typealias `Column` renamed to `ColName`
* jdbc: make it possible to override how Enum/Array is stored using JdbcConverter, default to toString() for enums
* jdbc: db.upsert() will now work as SQL MERGE for non-Postgres databases
* server: use<>() call will fail if called inside context {} for Extensions that need Server-level initialization
* server: allow body in DELETE requests, as sometimes people do that #117
* json: improved detection if inline classes should be rendered as their value type or a string
* jackson: DeserializationFeature.FAIL_ON_TRAILING_TOKENS is now enabled by default
* smtp: SmtpEmailSender will now log the successful email sent (to address and subject)
* openapi:
  * skip @AttrParam from OpenAPI spec
  * allow specifying SwaggerUI config parameters in openApi() function
  * skip @ApiResponse responseCode=default

# 1.6.16
* jdbc: restore exactly the same checksums of DBMigrator changesets containing comments

# 1.6.15
* core: log current active ENV when it is initialized
* jdbc: improve AlreadyExistsException message (always starts with errors.alreadyExists)
* jdbc: better handling of comments inside quoted strings in migration scripts
* csv: support for providing a different encoding than UTF-8 when parsing or generating
* openapi: adding openApi() to context will now add /openapi.json, /openapi.html and /openapi routes, which will also serve Swagger-UI automatically

# 1.6.14
* jdbc: add helpful details to Postgres exception "no hstore extension installed"
* core: TSID.deterministic introduced for using in test data generation
* json: TSGenerator updates
  * wraps records with enum keys into Partial<T> to avoid TS wanting all the keys to be present
  * outputs provided custom types as separate types and references them
  * accepts -o and -p arguments for simpler usage
  * optionally can generate test data from Kotlin objects using -t

# 1.6.13
* core: added PATCH method to TypedHttpClient
* jdbc: can use Config ENV vars in migration scripts
* jdbc-test: JUnitAssertionImprover removed, as Atrium 1.3+ improved error messages itself
* server: disallow direct assignments to `HttpExchange.responseType` to avoid confusion, `HttpExchange.send()` or `startResponse()` must be used to bypass renderers

# 1.6.12
* jdbc: fixed `neq` operator, it was accidentally broken in 1.6.11

# 1.6.11
* smtp: new module introduced for sending emails over SMTP
* server: expose Server.listen and bound Server.address as separate properties #93
* server: Server.use() can register extensions that implement multiple supported interfaces at the same time
* server: useOnly() will now add parser/renderer if it wasn't yet registered, to avoid confusion
* server: HttpExchange.rawBody can now be accessed more than once (is a lazy property now)
* jdbc: `distinct` and `notDistinct` operators introduced for where expressions
* jdbc: PooledDataSource: DB connections closed by server now correctly decrease connection pool size
* json: fixed some very specific regressions in TSGenerator

# 1.6.10
* server: `Server(InetSocketAddress(0))` can now be used to bind to any available port.
* server: make idle restarts quick #96
* server: initialize `HttpExchange.pathParams` even no route is matched (404), for decorators #82
* server: put list into queryParams if user specifies multiple parameters with the same name, introduce `e.queryList` #63
* jdbc: between operator introduced with open and closed ranges, also in and notIn
* jdbc: @NoTransaction can now be used on jobs
* jdbc: fixed usage of multiple different DataSources when there is an active transaction
* jdbc: allow calling of `PooledConnection.close()` multiple times #80
* jdbc: support for automatic persisting/mapping of arrays of @JvmInline classes (e.g. TSID)
* json: fix TSGenerator on Windows
* json: TSGenerator will now use more type-safe string template types for java.time classes, e.g. `${number}-${number}-${number}` instead of `string`
* slf4j: allow providing of `LOGGER_CLASS` via `.env` file if `Config.useEnvFile()` is called before any logging calls #92
* jackson: serialize enums using their toString() method by default, this fixes `openapi` module usage with `jackson` #88
* liquibase: do not close jdbc connection if it was passed by user #81

# 1.6.9
* core: logger(name) is now accessible without a class instance context
* core: Any.logger() will now take the closest non-anonymous superclass
* server: errors.on<SomeException>(StatusCode) convenience reified function added
* server: AssetsHandler will now allow serving of SPA index.html even if requested directory in assets exists
* jdbc: BaseCrudRepository.list() and by() now both have the suffix parameter
* jdbc: handle Postgres "cached plan must not change result type" exception by enabling autosave=conservative by default

# 1.6.8
* server: ErrorHandler now supports finding of handlers using exception super classes
* jobs: fix deprecated schedule() method implementation

# 1.6.7
* core: Converter can now force initialize type arguments' companion objects
* core: Decimal.absoluteValue and Decimal.sign introduced, like other numeric types in Kotlin
* core: Registry and TypedHttpClient moved from server module, so that JsonHttpClient can be used without the server
* server: HttpExchange.path<>(), query<>(), and session<>() now allow for automatic String conversion into value types
* server: Browser class better detects various iOS browsers that run on Apple WebKit
* jobs: deprecated non-Duration schedule() methods
* oauth: support for missing first or last names or locale
* jdbc: [initial support](jdbc/src/PostgresNotifier.kt) for Postgres listen/notify
* slf4j: fix length optimization of stack traces in StackTraceOptimizingLogger

# 1.6.6
* core: Converter will force initialize companion objects to better support `init { Converter.use {...} }`
* core: Converter now supports inline classes with multiple constructors automatically
* core: Converter now supports any static(String) method automatically, not only parse()
* core: Cache keepAlive function introduced to prolong external cache entries
* server: KeyCipher now uses base64UrlEncode() internally instead of plain base64
* jdbc: db.upsert() now uses `excluded` special table name for update part instead of setting values twice

# 1.6.5
* core: simple Cache with expiration timer implemented
* oauth: AppleOAuthClient updated and tested
* oauth: JWT helper class introduced
* jdbc: introduced db.upsertBatch()
* jobs: runOnce() introduced for convenience
* json: throw more descriptive errors from JsonNode.get() in case of missing keys

# 1.6.4
* core: added common value types for Email, Phone, and Password, also StringValue base class
* jdbc: introduced db.insertBatch()
* jdbc: deprecated Entity interface (UUID-based), which can be replaced with BaseEntity<UUID>
* jdbc: support SqlComputed with other operators, e.g. `where = "date" lte SqlComputed("currrent_date")`
* server: added path to annotated(), to make paths more visible in one place
* server: make a few more Server properties public (sessionStore, notFoundHandler, etc)
* server: bugfix after 1.6.0: wrap notFoundHandler correctly to produce correct 404 response
* server: NotFoundRoute introduced to be easily distinguishable from normal routes
* oauth: new experimental OAuth 2.0 login module

# 1.6.3
* server: added support for SSE (Server-Side Events) to `HttpExchange`
* json: improve parsing of complex types with parameters
* jdbc: introduce `NullableId` for entities with null ids until they are persisted.
* jdbc: introduce `UpdatableEntity` for optimistic locking in `BaseRepository.save`

# 1.6.2
* server: fix check for requested assets being inside of assets directory
* server: introduced FormDataRenderer
* core/json: KClass.createFrom will report missing parameters in a nicer way
* core: conversion of enum values from string is now case-insensitive
* json: can convert number to a custom type using opts.values
* json: TSGenerator: better detection of types of inline classes with several computed properties
* jdbc: upsert() now has skipUpdateFields parameter

# 1.6.1
* jdbc: and() introduced in addition to or() for more convenient composition of where expressions
* Kotlin and other dependency updates

# 1.6.0
* server: notFoundHandler is now decorated separately in each context, so that decorators can intercept and handle missing routes (e.g. CorsHandler)
* server: ErrorHandler will now omit the default message thrown by first() function ("List/Collection is empty.") and generate a standard 404 NotFound response
* server: ThrowableHandler now has HttpExchange as receiver for cleaner code, as it is not used in most handlers anyway
* server: ThrowableHandler returning null will now proceed with next handler, eventually producing error 500 if not handled
* server: Route is now a KAnnotatedElement, so that findAnnotation/hasAnnotation() functions from kotlin-reflect should be used on it (not backwards-compatible)
* jdbc: startDevDB() will now throw an exception if docker-compose returns a non-zero exit code
* jdbc: deprecated 1.4.x functions removed, please upgrade from 1.4.x to 1.5.0 first, fix deprecations, and then to 1.6.x
* openapi: new module that can generate OpenAPI json spec file for all routes in a context

# 1.5.6
* jdbc: DB_READONLY=true env var can be used to make the whole app read-only (e.g. when migrating the DB, instead of Heroku maintenance mode)
* jdbc: CrudRepository.get() now has optional forUpdate parameter
* HttpExchange.fileName() added for setting of Content-Disposition
* json bugfix: json within json (and '\') are now properly escaped
* json bugfix: render Any type as 'any' instead of 'string' in TSGenerator
* csv: new module for CSV parsing/generation

# 1.5.5
* jdbc: support reading of collections of Decimal from DB array columns
* jdbc: db.upsert() now has an optional "where" parameter
* server: AssetsHandler.headerModifier now gets file parameter to make decisions based on it
* server: allow having annotations on AssetsHandler for e.g. access checks

# 1.5.4
* json: ValueConverter can now be used to transform strings into types
* json: JsonNode.getList() signature fixed
* json: improve error message when trying to parse an empty stream
* json: JsonParser.readArray can now be used to stream json arrays without loading them into memory
* jdbc: close connection (return to pool) even if autoCommit/commit/rollback fails
* server: support for boolean query parameters without values, e.g. ?flag
* server: support for anonymous handler annotations (fixed in Kotlin 1.8)

# 1.5.3
* server: TypedHttpClient/JsonHttpClient uses a better logger name (from nearest user class)
* json/jackson: TypedHttpClient/JsonHttpClient now have overridable trimToLog property in case you need to process how requests/responses are logged
* jobs: JobRunner.schedule() now takes kotlin.time.Duration values
* jdbc: PooledDataSource - a simple and easy to configure connection pool (used in DBModule by default)
* jdbc: use HikariModule instead of DBModule if you still want to use Hikari (also add dependency on com.zaxxer:HikariCP)
* liquibase/serialization - dependencies updated

# 1.5.2
* core: experimental Decimal class to be used for monetary values, with numerical equality (unlike BigDecimal)
* core/json: use default values for explicitly passed nulls if property is not nullable
* core/json: unwrap InvocationTargetException, so that any validation exceptions thrown from data class constructors is propagated properly
* core/json: workaround for a bug in kotlin-reflect, which boxes null values even if nullable inline type is used: https://youtrack.jetbrains.com/issue/KT-57590
* json: TSGenerator can now receive additional library types to generate from command-line, e.g. klite.TSID
* i18n: do not trim translations by default (keep all whitespace)
* jdbc: support binding of Int values to data classes (DB usually returns integers as Long)
* jdbc: possibility to use table aliases when getting of columns from ResultSet with joins/using create (Postgres only)
* jdbc: allow whitespace between -- and keywords in changeset sql files
* server: default RequestLogFormatter will not log StatusCodeExceptions anymore
* server: useHashCodeAsETag() introduced to avoid sending of same responses
* server: run onStop handlers in reverse order of registration, so that e.g. connection pool is stopped after jobs are stopped
* jobs: do not start new jobs on shutdown while waiting for running jobs to finish

# 1.5.1
* json: ValueConverter.from() can now have access to the expected KType
* json: TSGenerator to generate TypeScript types for data classes/enums
* server: AppScope.async now is the standard async function that returns a Deferred. Use AppScope.launch if you want exceptions to be logged

# 1.5.0
* core: toValues() functions moved here from klite-jdbc
* core: TSID introduced as an alternative to UUID
* json: new lightweight json parser
* i18n: now uses the lightweight klite-json, not jackson
* jackson: package changed to klite.jackson to avoid conflicts with klite-json
* jackson: .parse<T> extension function now passes type parameters to Jackson, not only the main class
* jdbc: fromValues() was renamed to create()
* jdbc: switched db.select() <-> db.query(), taking "where" as a list or varargs, to allow for duplicated columns

The release is **not fully backwards-compatible**, however most old functions are provided as @Deprecated.

This will migrate the most important parts:
`find -name '*.kt' -exec sed -ri 's/klite.json./klite.jackson./; s/mapOfNotNull/notNullValues/; /db\.(query|select)/{N; s/db\.query/db.xxxselect/g; s/db\.select/db.query/g; s/mapOf/listOf/g; s/emptyMap/emptyList/g};' {} \; -exec sed -ri 's/db\.xxxselect/db\.select/; s/(db.update\(.*, )mapOf\((.*?\)), (mapOf\(.*?\))\)/\1\3, \2/; s/(db.delete\(.*, )mapOf\((.*?)\)/\1\2/' {} \;`

**Beware**: if you use the replacement above, then make sure that no deprecated query/select usages are left, i.e. check that all "where" maps are replaced with lists/varargs, which is done automatically only if on the same or next line.

You may also use IDEA migration of deprecations, but it will most likely [break code formatting, etc](https://youtrack.jetbrains.com/issue/KTIJ-24870).
Also, you may need to add some imports manually.

# 1.4.5
* jdbc: allow using $${json}$$ in migration scripts without treating it as substitutions
* jobs: do not unlock already running jobs after failing to lock (a problem for 3 instances with jobs or more)

# 1.4.4
* jobs: locked jobs did not release DB connection (bug introduced in 1.4.2)

# 1.4.3
* core: the new core module, to make it possible to use jdbc module without the server
* core: mapOfNotNull() now accepts keys of any type
* core: Converter.supports() now finds supported converters automatically, even if not used previously
* jdbc: db.count() introduced
* jdbc: box @JvmInline classes when loading from DB
* jdbc: allow same column multiple times in or() and deprecate NullOrOp()
* server: do not wrap exceptions already containing parameter name in annotated routes
* server: TypedHttpClient introduced as a more generic foundation for JsonHttpClient
* jackson: JsonHttpClient now accepts KType as non-reified parameters instead of KClass<*> for better type parameter support

# 1.4.2
* server: fallback to `docker compose` without dash
* server: shorten json stack traces by cutting repeating class/package names
* jdbc: emptyArray literal support '{}' - replaces previous EmptyOf() handling in toValues()
* jdbc: jsonb() and stuff that should work for both insert and update must use SqlComputed() now
* jdbc: CrudRepository now skips null where tokens for easier conditionals
* jobs: do not allow running of same jobs in parallel by default - useful in case of multiple instances

# 1.4.1
* server: skip "broken pipe" and "connection reset" exceptions by default
* server: request log now contains exception name that caused the status code to change
* jdbc: orExpr() and or() functions for where expressions
* jackson: be able to override JavaTimeModule settings more easily

# 1.4.0
* server: MimeTypes is now a singleton object
* jdbc: DBMigrator introduced to replace Liquibase in most cases
* jdbc: ClosedRange and OpenRange support
* jdbc: SqlExpr implements equals for easier parameter-based mocking
