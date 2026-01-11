package klite.jdbc

import klite.error
import klite.logger
import kotlinx.coroutines.ThreadContextElement
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Disable transaction for a route or a job. This will most likely leave the connection in auto-commit mode (depending on connection pool settings). */
@Target(FUNCTION, CLASS) annotation class NoTransaction

class Transaction(val db: DataSource): AutoCloseable {
  companion object {
    private val log = logger()
    private val threadLocal = ThreadLocal.withInitial { LinkedHashMap<DataSource, Transaction>() }
    fun current(db: DataSource): Transaction? = threadLocal.get()[db]
    fun current(): Transaction? = threadLocal.get().values.lastOrNull()
  }

  private var conn: Connection? = null
  val connection: Connection get() = conn ?: openConnection()

  private fun openConnection() = db.connection.apply {
    autoCommit = false
    conn = this
  }

  override fun close() = close(true)
  fun close(commit: Boolean = true) {
    try {
      conn?.apply {
        if (!autoCommit) {
          if (commit) commit() else rollback()
          autoCommit = true
        }
      }
    } catch (e: SQLException) {
      log.error("Failed to ${if (commit) "commit" else "rollback"}", e)
    } finally {
      try { conn?.close() } catch (e: Exception) { log.error("Failed to close $conn: $e") }
      conn = null
      detachFromThread()
    }
  }

  fun commit() = conn?.commit()
  fun rollback() = conn?.rollback()

  fun attachToThread() = this.also { threadLocal.get()[db] = it }

  fun detachFromThread() {
    val map = threadLocal.get()
    if (map[db] == this) map.remove(db)
    if (map.isEmpty()) threadLocal.remove()
  }
}

fun <R> DataSource.withConnection(block: Connection.() -> R): R {
  val tx = Transaction.current(this)
  return if (tx?.db == this) tx.connection.block()
         else connection.use(block)
}

class TransactionContext(val tx: Transaction? = Transaction.current()): ThreadContextElement<Transaction?>, AbstractCoroutineContextElement(Key) {
  companion object Key: CoroutineContext.Key<TransactionContext>

  override fun updateThreadContext(context: CoroutineContext) = tx.also { it?.attachToThread() }
  override fun restoreThreadContext(context: CoroutineContext, oldState: Transaction?) { tx?.detachFromThread() }
}
