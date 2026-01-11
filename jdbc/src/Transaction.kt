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

class Transaction: AutoCloseable {
  companion object {
    private val log = logger()
    private val threadLocal = ThreadLocal<Transaction>()
    fun current(): Transaction? = threadLocal.get()
  }

  private val connections = HashMap<DataSource, Connection>()
  fun connection(db: DataSource): Connection = connections.getOrPut(db) {
    db.connection.apply { autoCommit = false }
  }

  override fun close() = close(true)
  fun close(commit: Boolean = true) {
    connections.forEach { (_, conn) ->
      try {
        conn.apply {
          if (!autoCommit) {
            if (commit) commit() else rollback()
            autoCommit = true
          }
        }
      } catch (e: SQLException) {
        log.error("Failed to ${if (commit) "commit" else "rollback"}", e)
      } finally {
        try { conn.close() } catch (e: Exception) { log.error("Failed to close $conn: $e") }
      }
    }
    connections.clear()
    detachFromThread()
  }

  fun commit() = connections.forEach { (_, conn) -> conn.commit() }
  fun rollback() = connections.forEach { (_, conn) -> conn.rollback() }

  fun attachToThread() = this.also { threadLocal.set(it) }
  fun detachFromThread() = threadLocal.remove()
}

fun <R> DataSource.withConnection(block: Connection.() -> R): R {
  val tx = Transaction.current()
  return if (tx != null) tx.connection(this).block()
         else connection.use(block)
}

class TransactionContext(val tx: Transaction? = Transaction.current()): ThreadContextElement<Transaction?>, AbstractCoroutineContextElement(Key) {
  companion object Key: CoroutineContext.Key<TransactionContext>

  override fun updateThreadContext(context: CoroutineContext) = Transaction.current().also { tx?.attachToThread() }
  override fun restoreThreadContext(context: CoroutineContext, oldState: Transaction?) { oldState?.attachToThread() ?: tx?.detachFromThread() }
}
