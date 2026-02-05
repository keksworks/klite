# klite-jdbc

Provides simple extension functions for JDBC standard classes for a simple way to query a DB. [Transaction](src/Transaction.kt) management is also supported.

Design choices:
* Easy to write common things, like insert an entity or select a list of entities
* No need to write simple boilerplate SQL
* Possible to write complex SQL queries with joins and conditions when needed
* Full control how to create entities from the result set, but automapping when columns match entity properties

A more concise alternative to [Spring's JdbcTemplate](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/jdbc/core/JdbcTemplate.html),
and also generates SQL statements. Tested mostly with PostgreSQL.

Registration with Server instance:

```kotlin
  use<DBModule>() // to register a DataSource and connection pool using Config variables.
  use<DBMigrator>() // to migrate the DB using .sql files or changesets in code, see below
  use<RequestTransactionHandler>() // to enable per-request transactions
```

Stand-alone usage without [klite-server](../server) is also possible:
```kotlin
  val db = PooledDataSource(...) // or ConfigDataSource / HikariDataSource
  DBMigrator(db).migrate()
```

Usage:

```kotlin
  // obtain the registered DataSource (or declare it as a constructor parameter in your Repository class)
  val db = require<DataSource>()

  // basic insert into a table
  db.insert("table", mapOf("col1" to value, "col2" to value2))

  // insert all fields of an entity
  db.insert("table", entity.toValues())
  // redefine some entity field value before inserting
  db.upsert("table", entity.toValues(Entity::field to "another value"))
  // use @Column annotation to name DB columns differently than class fields

  // basic query from a table (mapper runs in the context of ResultSet)
  db.select("table", "column" to value) { MyEntity(getId(), getString("column")) }
  // or if all entity properties are contained in the result set
  db.select("table", "column" to value) { create<MyEntity>() }
  // or simply
  db.select<MyEntity>("table", "column" to value)

  // you can write more complex custom SQL conditions easily
  db.select("table", sql("(column is null or column >= ?)", 10))
  // where can also be written in a type-safe way, and some common operators are available as infix functions (lt, gt, lte, gte, like, ilike, etc)
  db.select("table", MyEntity::column gte 123)

  // where tokens are "and"-ed together, nulls filtered out, so convenient conditionals are possible
  db.select("table", MyEntity::column gte 123, (MyEntity::other to something).takeIf { something != null })
  // or another option is to use list filtering
  db.select("table", notNullValues(MyEntity::column gte 123, MyEntity::other to something))

  // "or" is also possible
  db.select("table", MyEntity::column gte 123, or(MyEntity::other to something, "hello" to "world"))

  // more advanced query with suffix and create() auto-mapper
  db.select("table", "col1" to notNull, "col2" gte  value, suffix = "order by col3 limit 10") { create<MyEntity>() }
  // single row, with joins, etc
  db.select("table1 left join table2 on table1.id = table2.megaId", listOf("table2.field" to value), "limit 1") {
    create<MyEntity>(MyEntity::other to create<OtherEntity>("table2.")) // you can provide table alias to create (PostgresSQL only)
  }.first()

  // or you can write full SQL manually using db.query() and db.exec()
```

See [all available operations](src/JdbcExtensions.kt) and [comparison operators](src/SqlExpr.kt).

All the above can run without a transaction: in this case, every query will obtain and release its own connection from the pool,
in autocommit mode. If [Transaction](src/Transaction.kt) is active, then it will obtain a connection on first use,
set autoCommit=false and reuse it until the transaction is closed with either commit or rollback.

In query mappers you can either use ResultSet methods and extensions to build your entities or use the
[ResultSet.create](src/Values.kt). Likewise, [Any.toValues](../core/src/Values.kt) is provided to simplify
conversion of entities to Maps for use with insert/update/upsert.

[JdbcConverter](src/JdbcConverter.kt) can be used to register conversion of custom types to be sent to the DB.

## Base entity classes and repositories

[BaseRepository](src/Repository.kt) and [BaseCrudRepository](src/Repository.kt) are provided for convenience.

They work with entity classes implementing `BaseEntity<ID>`, where you can provide your own ID class, like UUID or [TSID](../core/src/TSID.kt).

Experimental:
* `NullableId<ID>` is also provided if you prefer to assign ids only during saving.
* `UpdatabaleEntity` can be used for [optimistic locking](https://en.wikipedia.org/wiki/Optimistic_concurrency_control) when saving, not letting concurrent users overwrite each other's changes.

## Migrations

[DBMigrator](src/migrator/DBMigrator.kt) is provided for simple SQL-based DB migrations, it supports a very similar syntax to [Liquibase SQL Format](https://docs.liquibase.com/concepts/basic/sql-format.html), see [sample](../sample/db/db.sql).

Advantages over Liquibase:
* Small and simple, doesn't include vulnerable dependencies nor requires java.desktop module
* No mandatory author name, which can be useless when pair programming or caring about collective code ownership
* Filepath is not part of changeset unique ID, enabling moving changesets between files easily (refactoring)
* To minimize conflicts, every team can structure their IDs in their own way, e.g. prefixing with author or using date-time notation
* More reliable locking that will not be left due to crash (PG advisory lock)
* Provides a simpler way to treat changes and failures with onChange and onFail attributes
* Allows changesets to modify the db_changelog table for refactoring (it is re-read if changes are detected)
* Allows writing changesets in Kotlin code via [ChangeSet](src/migrator/ChangeSet.kt) constructor
* To migrate from Liquibase, use `--include` [migrator/liquibase.sql](src/migrator/liquibase.sql), or copy the changeset with your modifications to your `db.sql`

Advantages over Flyway:
* Multiple changesets per file, convenient to work with
* Can structure changesets as maintainable code
* Can refactor/control handling of changes

Not supported:
* Non-sql file formats, but can use Kotlin code
* No undo (usually you don't undo a prod DB)
* No preconditions, but e.g. `onFail:SKIP` can be easier to use
* No generation of scripts for existing DB schema — this is best to be done manually, or just existing tools like pg_dump
* Mostly tested with PostgreSQL, PRs for other DBs welcome
  * For other databases you may have to redefine [migrator/init.sql](src/migrator/init.sql) in classpath to use more compatible column types for the `db_changelog` table

#### Different DB users for migration and running the app

It is better for security to use a user with fewer rights for the running application.

Your changesets can actually create this user and grant only *select/insert/update* permissions, but not e.g. *create/drop table*.

```kotlin
  // start dev db, for developer convenience
  if (Config.isDev) startDevDB()
  // migrate with the default all-rights user
  use<DBMigrator>()
  // use app user for the application DataSource and connection pool
  useAppDBUser()
  use(DBModule {
    // override any other connection pool settings
  })
```

### Best practices

It's more convenient to treat DB objects as maintainable code, e.g.
* Create a separate .sql file for an entity like [users.sql](../sample/db/users.sql)
* Use a dot notation for changeset names, like `users.personalCode`, which adds users.personalCode column
* Backwards-compatible changesets between deploys, allowing for rollback of the app with already updated DB
* You can add destructive changesets for future right away with some non-existent context, e.g. `context:TODO`
* Run the same changesets before DB-related unit tests
