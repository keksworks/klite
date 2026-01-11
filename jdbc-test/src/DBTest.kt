package klite.jdbc

import klite.Config
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import javax.sql.DataSource

/**
 * Base class for DB-dependent unit tests.
 * Will try to start dev DB via docker-compose automatically.
 * Transaction rollback will happen after each test, so that all tests can run in a clean state.
 */
abstract class DBTest {
  companion object {
    init {
      Config["ENV"] = "test"
      Config.useEnvFile()
      DockerCompose.startDB()
    }

    @JvmStatic protected val db: DataSource by lazy { DBModule().dataSource }
  }

  @BeforeEach open fun startTx() {
    Transaction().attachToThread()
  }

  @AfterEach open fun rollbackTx() {
    Transaction.current()?.close(commit = false)
  }
}
