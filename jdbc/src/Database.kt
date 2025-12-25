package klite.jdbc

import org.intellij.lang.annotations.Language
import javax.sql.DataSource

class Database private constructor(val db: DataSource) {
  companion object {
    fun of(db: DataSource) = Database(db)
  }

  fun query(@Language("SQL") select: String) = Query(StringBuilder(select))
  fun select(@Language("SQL", prefix = selectFrom) table: String) = Query(StringBuilder(selectFrom).append(q(table)))

  inner class Query internal constructor(@Language("SQL") select: StringBuilder): QueryExecutor(select) {
    fun join(@Language("SQL", prefix = selectFrom) table: String, on: String) = this.also {
      select.append(" join ").append(q(table)).append(" on ").append(on)
    }

    fun where(where: Where) = this.also { this.where += whereConvert(where) }
    fun where(vararg where: ColValue?) = where(where.filterNotNull())

    fun suffix(@Language("SQL", prefix = selectFromTable) suffix: String) = this.also { this.suffix += suffix }
    fun order(by: String, asc: Boolean = true) = this.also { suffix = "order by $by" + (if (asc) "" else " desc" ) }
  }

  open inner class QueryExecutor internal constructor(@Language("SQL") val select: StringBuilder) {
    protected val where = mutableListOf<ColValue>()
    protected var suffix = ""

    fun <R> map(mapper: Mapper<R>): Sequence<R> = sequence {
      db.withStatement("${select}${whereExpr(where)} $suffix") {
        setAll(whereValues(where))
        executeQuery().use { rs ->
          rs.populatePgColumnNameIndex(select.toString())
          while (rs.next()) yield(rs.mapper())
        }
      }
    }

    inline fun <reified R> map(): Sequence<R> = map { create() }

    fun <R> one(mapper: Mapper<R>) = map(mapper).firstOrNull() ?: throw NoSuchElementException("Not found")
    inline fun <reified R> one(): R = one { create() }
  }
}

fun DataSource.select(@Language("SQL", prefix = selectFrom) table: String) = Database.of(this).select(table)
fun DataSource.query(@Language("SQL") select: String) = Database.of(this).query(select)
