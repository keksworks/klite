package klite.jdbc

import klite.*
import klite.jdbc.PooledDataSource.PooledConnection
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import org.postgresql.PGConnection
import org.postgresql.PGNotification
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Deprecated("Experimental", level = DeprecationLevel.WARNING)
class PostgresNotifier<K: Any>(vararg channels: K): Extension {
  private val channels = channels.associate { it.toString() to Channel<String>(UNLIMITED) }
  private lateinit var db: DataSource

  fun send(channel: K, payload: String = "") = db.notify(channel.toString(), payload)
  suspend fun receive(channel: K) = channels[channel.toString()]!!.receive()

  override fun install(server: Server) = server.run {
    db = require<DataSource>()
    val listener = thread(name = this::class.simpleName, isDaemon = true) {
      db.consumeNotifications(channels.keys) {
        channels[it.name]?.trySend(it.parameter)
      }
    }
    register(this)
    server.onStop { listener.interrupt() }
  }
}

/** Send Postgres notification to the specified channel. Delivered after commit */
fun DataSource.notify(channel: String, payload: String = "") = withStatement("select pg_notify(?, ?)") {
  setAll(sequenceOf(channel, payload))
  executeQuery().run { next() }
}

/** Dedicate a separate thread to listen to Postgres notifications and send them to the corresponding channels. */
fun DataSource.consumeNotifications(channels: Iterable<String>, timeout: Duration = 10.seconds, consumer: (notification: PGNotification) -> Unit) {
  val thread = Thread.currentThread()
  val log = logger<PostgresNotifier<*>>()
  while (!thread.isInterrupted) {
    try {
      withConnection {
        listen(channels)
        log.info("Listening to Postgres notifications on channels: $channels")
        unwrapOrNull<PooledConnection>()?.longUsed = true
        while (!thread.isInterrupted) {
          pgNotifications(timeout).forEach { consumer(it) }
        }
      }
    } catch (ex: SQLException) {
      log.warn("Notification listener interrupted due to: ${ex.message}. Retrying...")
      Thread.sleep(100)
    }
  }
}

fun Connection.listen(channels: Iterable<String>) = createStatement().use { s ->
  channels.forEach { s.execute("listen \"$it\"") }
}

fun Connection.pgNotifications(timeout: Duration): Array<PGNotification> =
  unwrap(PGConnection::class.java).getNotifications(timeout.inWholeMilliseconds.toInt())
