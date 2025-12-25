package klite.jdbc

import org.intellij.lang.annotations.Language
import javax.sql.DataSource

class Database(val db: DataSource) {
  fun query(@Language("SQL") select: String) = Query(select)
  fun select(table: String) = Query("select * from $table")

  inner class Query(@Language("SQL") val select: String) {
    private val where = mutableListOf<ColValue>()
    private var suffix = ""

    fun where(where: Where) = this.also { this.where += whereConvert(where) }
    fun where(vararg where: ColValue?) = where(where.filterNotNull())
    fun order(by: String, asc: Boolean = true) = this.also { suffix = "order by $by" + (if (asc) "" else " desc" ) }

    fun <R> list(mapper: Mapper<R>): Sequence<R> = sequence {
      db.withStatement("${select}${whereExpr(where)} $suffix") {
        setAll(whereValues(where))
        executeQuery().use { rs ->
          rs.populatePgColumnNameIndex(select)
          while (rs.next()) yield(rs.mapper())
        }
      }
    }
  }
}

fun main() {
  val db = Database(ConfigDataSource())
  db.select("users").where()
}
