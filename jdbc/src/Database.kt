package klite.jdbc

import org.intellij.lang.annotations.Language
import javax.sql.DataSource

class Database private constructor(val db: DataSource) {
  companion object {
    fun of(db: DataSource) = Database(db)
  }

  fun query(@Language("SQL") select: String) = Query<Any>(StringBuilder(select))
  fun select(@Language("SQL", prefix = selectFrom) table: String) = Query<Any>(StringBuilder(selectFrom).append(q(table)))

  inner class Query<R> internal constructor(
    @Language("SQL") select: StringBuilder,
    mapper: Mapper<R> = { create() }
  ): QueryExecutor<R>(select, mapper) {
    fun join(@Language("SQL", prefix = selectFrom) table: String, on: String) = this.also {
      select.append(" join ").append(q(table)).append(" on ").append(on)
    }

    fun where(where: Where) = this.also { this.where += whereConvert(where) }
    fun where(vararg where: ColValue?) = where(where.filterNotNull())
    fun where(where: ColValue?) = this.also { where?.let { this.where += it } }

    fun suffix(@Language("SQL", prefix = selectFromTable) suffix: String) = this.also { this.suffix.append(' ').append(suffix) }
    fun order(by: ColName, asc: Boolean = true) = suffix("order by " + q(name(by)) + (if (asc) "" else " desc" ))
    fun groupBy(vararg cols: ColName) = suffix("group by " + cols.joinToString { q(name(it)) })
    fun forUpdate(lockMode: String = if (isPostgres) "no key" else "") = suffix("for $lockMode update")

    fun <T> map(mapper: Mapper<T>) = (this as Query<T>).also { this.mapper = mapper }
  }

  open inner class QueryExecutor<R> internal constructor(
    @Language("SQL") val select: StringBuilder,
    protected var mapper: Mapper<R>
  ) {
    protected val where = mutableListOf<ColValue>()
    protected var suffix = StringBuilder()

    fun run(): Sequence<R> = sequence {
      db.withStatement("${select}${whereExpr(where)}$suffix") {
        setAll(whereValues(where))
        executeQuery().use { rs ->
          rs.populatePgColumnNameIndex(select.toString())
          while (rs.next()) yield(rs.mapper())
        }
      }
    }

    fun list() = run().toList()

    fun one() = run().firstOrNull() ?: throw NoSuchElementException("Not found")
  }
}

fun DataSource.select(@Language("SQL", prefix = selectFrom) table: String) = Database.of(this).select(table)
fun DataSource.query(@Language("SQL") select: String) = Database.of(this).query(select)
