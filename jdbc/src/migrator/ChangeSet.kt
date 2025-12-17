package klite.jdbc

import klite.jdbc.ChangeSet.On.FAIL
import klite.toValuesSkipping
import klite.trimToNull
import org.intellij.lang.annotations.Language
import java.sql.ResultSet
import javax.sql.DataSource

data class ChangeSet(
  override val id: String,
  @Language("SQL") val sql: CharSequence = StringBuilder(),
  val context: String? = null,
  val onChange: On = FAIL,
  val onFail: On = FAIL,
  val separator: String? = ";",
  val filePath: String? = null,
  var checksum: Long? = null
): BaseEntity<String> {
  var rowsAffected = 0
  val statements: List<String> by lazy {
    (if (separator != null) sql.split(separator) else listOf(sql.toString())).mapNotNull { it.trimToNull() }
  }
  private fun calcChecksum() = statements.fold(0L) { r, s -> r * 89 + s.replace("\\s*\n\\s*".toRegex(), "\n").hashCode() }

  init { if (sql is String) finish() }

  fun matches(contexts: Set<String>) =
    if (context == null) true
    else if (context.startsWith("!")) context.substring(1) !in contexts
    else if (context.contains(",")) context.split(",").any { it.trim() in contexts }
    else context in contexts

  internal fun addLine(line: String) {
    if ((sql as StringBuilder).isNotEmpty()) sql.append("\n")
    sql.append(line)
  }

  internal fun finish() = apply {
    if (checksum == null) checksum = calcChecksum()
  }

  enum class On { FAIL, RUN, SKIP, MARK_RAN }
}

class ChangeSetRepository(db: DataSource): BaseCrudRepository<ChangeSet, String>(db, "db_changelog") {
  override val defaultOrder = ""
  override fun ChangeSet.persister() = toValuesSkipping(ChangeSet::separator, ChangeSet::sql, ChangeSet::onChange, ChangeSet::onFail)
  override fun ResultSet.mapper() = ChangeSet(getString("id"), "", getString("context"), filePath = getString("filepath"), checksum = getLong("checksum"))
}
