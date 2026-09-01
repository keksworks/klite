package klite.jdbc

import klite.logger
import klite.trimToNull
import klite.warn
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Types
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource
import kotlin.text.RegexOption.IGNORE_CASE
import kotlin.text.RegexOption.MULTILINE

private val DataSource.url get() = unwrapOrNull<ConfigDataSource>()?.url
private val dbPostgresIndicators = ConcurrentHashMap<DataSource, Boolean>()

val DataSource.isPostgres get() = dbPostgresIndicators.getOrPut(this) {
  (url ?: withConnection { metaData.url }).contains("postgresql")
}

fun Connection.lock(on: String) { call("pg_advisory_lock", lockKey(on)) }
fun Connection.tryLock(on: String): Boolean = tryCall("pg_try_advisory_lock", lockKey(on))
fun Connection.unlock(on: String): Boolean = tryCall("pg_advisory_unlock", lockKey(on))

// TODO: should these be deprecated? unlock() must be run always on the same connection
fun DataSource.lock(on: String) = withConnection { lock(on) }
fun DataSource.tryLock(on: String): Boolean = withConnection { tryLock(on) }
fun DataSource.unlock(on: String): Boolean = withConnection { unlock(on) }

private fun lockKey(on: String): Long = on.fold(0L) { acc, char -> 31L * acc + char.code }

private fun Connection.tryCall(callable: String, param: Long): Boolean =
  (call(callable, param, returnSqlType = Types.BOOLEAN) == true).also {
    if (!it) logger().warn("$callable: $it")
  }

private val columnNameIndexMapField = runCatching {
  Class.forName("org.postgresql.jdbc.PgResultSet").getDeclaredField("columnNameIndexMap").apply { trySetAccessible() }
}.getOrNull()

/**
 * Pre-populates PgResultSet.columnNameIndexMap with duplicate (joined columns) prefixed with their index,
 * making it possible to use getString("alias.id") to get other table's "id" column, etc.
 */
internal fun ResultSet.populatePgColumnNameIndex(select: String) {
  // TODO: make it work with other DBs as well, by maybe wrapping a Connection/ResultSet and overriding getString and others?
  if (columnNameIndexMapField == null || !select.contains("join", ignoreCase = true)) return
  val rs = unwrapOrNull(columnNameIndexMapField.declaringClass as Class<ResultSet>) ?: return
  val md = metaData
  val map = HashMap<String, Int>()
  val joinAliases = joinAliases(select)
  var joinCount = 0
  for (i in 1..md.columnCount) {
    val label = md.getColumnLabel(i)
    if (label == "id") joinCount++
    map.putIfAbsent(label, i)
    if (joinCount > 1) map.putIfAbsent("${joinAliases.getOrNull(joinCount - 2) ?: joinCount}.$label", i)
  }
  columnNameIndexMapField.set(rs, map)
}

private val joinRegex = "\\bjoin\\s+(\\w+?)(\\s+as)?(\\s+(\\w+?))?\\s+(on|using)\\b".toRegex(setOf(IGNORE_CASE, MULTILINE))
internal fun joinAliases(select: String) = joinRegex.findAll(select).map { it.groupValues[4].trimToNull() ?: it.groupValues[1] }.toList()
