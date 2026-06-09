# klite-liquibase

Provides [LiquibaseModule](src/LiquibaseModule.kt) to migrate the DB on application start.
Note that there is a much lighter-weight [DBMigrator](../jdbc/src/migrator/DBMigrator.kt) in the [jdbc](../jdbc) module.

As Liquibase uses java.logging module, this module will also redirect it to slf4j to be used together with [klite-jdbc](../jdbc).

Consider using Liquibase's [sql format](https://docs.liquibase.com/concepts/basic/sql-format.html) instead of xml.

## Different DB user for migration and running

It is a best practice in terms of security to use a user with fewer rights for the running application.

Your Liquibase scripts can actually create this user with only *insert/update/select* permissions.

```kotlin
  // start dev db, for developer convenience
  if (Config.isDev) DockerCompose.startDB()
  // migrate with the default all-rights user
  use(LiquibaseModule())
  // use app user for the application DataSource and connection pool
  useAppDBUser()
  use(DBModule {
    // override any other connection pool settings
  })
```
