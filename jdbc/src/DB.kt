package klite.jdbc

import java.sql.Connection
import java.sql.Wrapper
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource

/** DataSource or Connection */
typealias DB = Wrapper

fun <R> DB.withConnection(block: Connection.() -> R): R {
  if (this is Connection) return use(block)
  if (this !is DataSource) unsupported()
  val tx = Transaction.current()
  return if (tx != null) tx.connection(this).block()
  else connection.use(block)
}

private val DataSource.url get() = unwrapOrNull<ConfigDataSource>()?.url
private val dbPostgresIndicators = ConcurrentHashMap<DataSource, Boolean>()

internal val String.isPostgresUrl get() = contains("postgres")

val DB.isPostgres: Boolean get() = when (this) {
  is DataSource -> dbPostgresIndicators.getOrPut(this) {
    (url ?: withConnection { metaData.url }).isPostgresUrl
  }
  is Connection -> metaData.url.isPostgresUrl
  else -> unsupported()
}

private fun DB.unsupported(): Nothing = throw UnsupportedOperationException("$this must be a Connection or DataSource")
