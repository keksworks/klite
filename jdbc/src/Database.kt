package klite.jdbc

import org.intellij.lang.annotations.Language
import javax.sql.DataSource

class Database(val db: DataSource) {
  fun query(@Language("SQL") select: String) = Query(select)
  fun select(table: String) = Query("select * from $table")

  inner class Query(@Language("SQL") val select: String) {
    private val where = mutableListOf<ColValue>()
    private var suffix = ""

    fun where(where: Where) = this.also { this.where += where }
    fun where(vararg where: ColValue?) = where(where.filterNotNull())
    fun order(by: String, asc: Boolean = true) = this.also { suffix = "order by $by" + (if (asc) "" else " desc" ) }

    fun <R> list(mapper: Mapper<R>): Sequence<R> = sequence {
      val qWhere = whereConvert(where)
      db.withConnection {
        prepareStatement("${select}${whereExpr(qWhere)} ${suffix}").use { stmt ->
          stmt.setAll(whereValues(qWhere))
          stmt.executeQuery().use { rs ->
            rs.populatePgColumnNameIndex(select)
            while (rs.next()) yield(mapper(rs))
          }
        }
      }
    }
  }
}

fun main() {
  val db = Database(ConfigDataSource())
  db.select("users").where()
}
