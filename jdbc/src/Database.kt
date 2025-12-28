package klite.jdbc

import org.intellij.lang.annotations.Language
import javax.sql.DataSource

class Database private constructor(val db: DataSource) {
  companion object {
    fun of(db: DataSource) = Database(db)
  }

  fun query(@Language("SQL") select: String) = Query<Any>(StringBuilder(select))
  fun select(@Language("SQL", prefix = selectFrom) table: String) = Query<Any>(StringBuilder(selectFrom).append(q(table)))

  fun update(@Language("SQL", prefix = "update") table: String) = Update(table)
  fun delete(@Language("SQL", prefix = "delete from") table: String) = Delete(table)
  fun insert(@Language("SQL", prefix = "insert into") table: String) = Insert(table)
  fun upsert(@Language("SQL", prefix = "merge into") table: String, uniqueFields: Set<ColName> = setOf("id")) = Update(table) // TODO

  inner class Query<R> internal constructor(
    @Language("SQL") select: StringBuilder,
    mapper: Mapper<R> = { create() }
  ): QueryExecutor<R>(select, mapper), WhereHandler<Query<R>> {
    fun join(@Language("SQL", prefix = selectFrom) table: String, on: String) = this.also {
      select.append(" join ").append(q(table)).append(" on ").append(on)
    }

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
    val where = mutableListOf<ColValue>() // TODO: not public
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

  internal interface WhereHandler<P: WhereHandler<P>> {
    val where: MutableList<ColValue>

    fun where(where: Where): P = (this as P).also { this.where += whereConvert(where) }
    fun where(vararg where: ColValue?): P = where(where.filterNotNull())
    fun where(where: ColValue?): P = (this as P).also { where?.let { this.where += it } }
  }

  internal interface ValuesHandler<P: ValuesHandler<P>> {
    val values: MutableMap<ColName, Any?>

    fun set(value: ColValue) = (this as P).also { values += value }
    fun set(vararg values: ColValue) = (this as P).also { values.forEach { v -> this.values += v } }
  }

  inner class Update internal constructor(@Language("SQL", prefix = "update") val table: String): WhereHandler<Update>, ValuesHandler<Update> {
    override val where = mutableListOf<ColValue>() // TODO: not public
    override val values = mutableMapOf<ColName, Any?>() // TODO: not public

    fun run() = db.exec("update ${q(table)}" + setExpr(values) + whereExpr(where), setValues(values), whereValues(where))
  }

  inner class Delete internal constructor(@Language("SQL", prefix = "delete from") val table: String): WhereHandler<Delete> {
    override val where = mutableListOf<ColValue>() // TODO: not public

    fun run() = db.exec("delete from ${q(table)}" + whereExpr(where), whereValues(where))
  }

  inner class Insert internal constructor(@Language("SQL", prefix = "insert into") val table: String): ValuesHandler<Insert> {
    override val values = mutableMapOf<ColName, Any?>() // TODO: not public

    fun run() = db.exec(insertExpr(table, values), setValues(values))
  }
}

fun DataSource.select(@Language("SQL", prefix = selectFrom) table: String) = Database.of(this).select(table)
fun DataSource.query(@Language("SQL") select: String) = Database.of(this).query(select)
