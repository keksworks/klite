package klite.jdbc

import klite.*
import java.lang.System.currentTimeMillis
import java.lang.Thread.currentThread
import java.sql.Connection
import java.sql.SQLException
import java.sql.SQLFeatureNotSupportedException
import java.sql.SQLTimeoutException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Simple DB connection pool that opens connections on demand up to [maxSize].
 * Optional [leakCheckThreshold] can be used for debugging of possibly leaked connections.
 * ApplicationName is set to the name of the retrieving thread/request, so that you can see who is holding the connection on the DB side.
 */
@Suppress("UNCHECKED_CAST")
class PooledDataSource(
  val db: DataSource = ConfigDataSource(),
  val maxSize: Int = Config.dbPoolMaxSize,
  val timeout: Duration = 5.seconds,
  val queryTimeout: Duration = timeout * 2,
  val leakCheckThreshold: Duration? = null
): DataSource by db, AutoCloseable {
  private val log = logger()
  private val counter = AtomicInteger()
  internal val size = AtomicInteger()
  internal val available = ArrayBlockingQueue<PooledConnection>(maxSize)
  internal val used = ConcurrentHashMap<PooledConnection, Used>(maxSize)

  init {
    Metrics.register("dbPool") {
      mapOf("max" to maxSize, "size" to size.get(), "available" to available.size, "used" to used.size)
    }
  }

  data class Used(val since: Long = currentTimeMillis(), val threadName: String = currentThread().name)

  private val leakChecker = leakCheckThreshold?.let {
    thread(name = "$this:leakChecker", isDaemon = true) {
      while (!Thread.interrupted()) {
        val now = currentTimeMillis()
        used.entries.removeIf { (conn, used) ->
          val usedForMs = now - used.since
          if (runCatching { conn.isClosed }.getOrNull() == true) {
            log.warn("Dropping closed $conn, used for ${usedForMs / 1000}s, acquired by ${used.threadName}")
            size.decrementAndGet()
            true
          } else {
            if (usedForMs >= it.inWholeMilliseconds)
              log.error("Possible leaked $conn, used for ${usedForMs / 1000}s, acquired by ${used.threadName}")
            false
          }
        }
        try { Thread.sleep(it.inWholeMilliseconds / 10) } catch (e: InterruptedException) { break }
      }
    }
  }

  override fun getConnection(username: String?, password: String?) = throw SQLFeatureNotSupportedException("Use getConnection()")
  override fun getConnection(): PooledConnection {
    var conn: PooledConnection?
    do {
      conn = available.poll() ?: size.incrementAndGet().let { newSize ->
        if (newSize <= maxSize) try {
          PooledConnection(db.connection).also { log.info("New connection $newSize/$maxSize: $it") }
        } catch (e: Exception) {
          size.decrementAndGet(); throw e
        } else {
          size.decrementAndGet()
          available.poll(timeout.inWholeMilliseconds, MILLISECONDS) ?:
            throw SQLTimeoutException("No available connection after $timeout; used: ${used.size}/$size")
        }
      }
      try { conn.checkBySetApplicationName() } catch (e: Exception) {
        log.warn("Dropping failed $conn, age ${conn.ageMs / 1000}s, available: ${available.size}/$size: $e")
        size.decrementAndGet()
        conn = null
      }
    } while (conn == null)
    used[conn] = Used()
    return conn
  }

  override fun close() {
    leakChecker?.interrupt()
    available.removeIf { it.reallyClose(); true }
    used.keys.removeIf { it.reallyClose(); true }
  }

  inner class PooledConnection(private val conn: Connection): Connection by conn {
    val count = counter.incrementAndGet()
    val since = currentTimeMillis()
    init {
      try { setNetworkTimeout(null, queryTimeout.inWholeMilliseconds.toInt()) }
      catch (e: Exception) { log.warn("Failed to set network timeout for $this: $e") }
    }

    val ageMs get() = currentTimeMillis() - since

    internal fun checkBySetApplicationName() { applicationName = currentThread().name }

    override fun close() {
      try {
        if (used.remove(this) != null) {
          if (!conn.autoCommit) conn.rollback()
          available += this
        } else log.warn("Trying to close already returned $this")
      } catch (e: SQLException) {
        log.warn("Failed to return $this: $e")
        size.decrementAndGet()
      }
    }

    internal fun reallyClose() = try {
      log.info("Closing $this, age ${ageMs / 1000}s")
      conn.close()
    } catch (e: SQLException) {
      log.warn("Failed to close $this: $e")
    }

    override fun toString() = "Pooled#${count}:$conn"

    override fun isWrapperFor(iface: Class<*>) = iface.isAssignableFrom(conn.javaClass)
    override fun <T> unwrap(iface: Class<T>): T = iface.cast(conn)
  }

  override fun isWrapperFor(iface: Class<*>) = iface.isAssignableFrom(db.javaClass)
  override fun <T> unwrap(iface: Class<T>): T = iface.cast(db)
}
