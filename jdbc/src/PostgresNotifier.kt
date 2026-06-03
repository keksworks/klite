package klite.jdbc

import klite.info
import klite.logger
import klite.warn
import org.postgresql.PGConnection
import org.postgresql.PGNotification
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Deprecated("Experimental", level = DeprecationLevel.WARNING)
abstract class PostgresListener(protected val db: DataSource, vararg channels: String): AutoCloseable {
  protected val channels = channels.toList().takeIf { it.isNotEmpty() } ?: listOf(javaClass.simpleName)
  protected val listener = thread(name = javaClass.simpleName, isDaemon = true) {
    db.consumeNotifications(this.channels) {
      listen(it.name, it.parameter)
    }
  }

  protected fun notify(channel: String = channels.first(), payload: String) = db.notify(channel, payload)
  protected abstract fun listen(channel: String, payload: String)
  override fun close() = listener.interrupt()
}

/** Send Postgres notification to the specified channel. Delivered after commit */
@IgnorableReturnValue
fun DataSource.notify(channel: String, payload: String = "") = withStatement("select pg_notify(?, ?)") {
  setAll(listOf(channel, payload))
  executeQuery().run { next() }
}

/** Dedicate a separate thread to listen to Postgres notifications */
fun DataSource.consumeNotifications(channels: Iterable<String>, timeout: Duration = 10.seconds, connLifetime: Duration = 20.minutes, consumer: (notification: PGNotification) -> Unit) {
  val thread = Thread.currentThread()
  val log = logger<PostgresListener>()
  val db = unwrapOrNull<DataSource>() ?: this
  while (!thread.isInterrupted) {
    db.connection.use { conn ->
      try {
        conn.listen(channels)
        log.info("Listening to Postgres notifications on channels: $channels using $conn")
        var times = (connLifetime / timeout).toInt()
        while (!thread.isInterrupted && --times >= 0) {
          conn.pgNotifications(timeout).forEach { consumer(it) }
        }
      } catch (ex: SQLException) {
        log.warn("$channels listener interrupted due to: ${ex.message} using $conn. Retrying...")
        Thread.sleep(100)
      }
    }
  }
}

fun Connection.listen(channels: Iterable<String>) = createStatement().use { s ->
  channels.forEach { s.execute("listen \"$it\"") }
}

fun Connection.pgNotifications(timeout: Duration): Array<PGNotification> =
  unwrap(PGConnection::class.java).getNotifications(timeout.inWholeMilliseconds.toInt())
