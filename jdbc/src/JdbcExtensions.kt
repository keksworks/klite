@file:Suppress("NAME_SHADOWING", "NOTHING_TO_INLINE")
package klite.jdbc

import klite.Config
import klite.Decimal
import org.intellij.lang.annotations.Language
import java.io.InputStream
import java.sql.*
import java.sql.Statement.NO_GENERATED_KEYS
import java.sql.Statement.RETURN_GENERATED_KEYS
import javax.sql.DataSource
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation

val namesToQuote = mutableSetOf("limit", "offset", "check", "table", "column", "constraint", "default", "desc", "distinct", "end", "foreign", "from", "grant", "group", "primary", "user")

fun DataSource.exec(@Language("SQL") expr: String, vararg values: Any?): Int = exec(expr, values.asSequence())
fun DataSource.exec(@Language("SQL") expr: String, values: Sequence<Any?> = emptySequence(), keys: Int = NO_GENERATED_KEYS, callback: (Statement.() -> Unit)? = null): Int =
  execBatch(expr, sequenceOf(values), keys, callback).first()

fun DataSource.execBatch(@Language("SQL") expr: String, values: Sequence<Sequence<Any?>>, keys: Int = NO_GENERATED_KEYS, callback: (Statement.() -> Unit)? = null): IntArray {
  val i = values.iterator()
  if (!i.hasNext()) return intArrayOf()
  return withStatement(expr, keys) {
    var count = 0
    while (i.hasNext()) {
      setAll(i.next())
      if (count > 0 || i.hasNext()) addBatch()
      count++
    }
    (if (count == 1) intArrayOf(executeUpdate()) else executeBatch()).also {
      if (callback != null) callback()
    }
  }
}

inline fun <R> DataSource.withStatement(@Language("SQL") sql: String, keys: Int = NO_GENERATED_KEYS, block: PreparedStatement.() -> R): R = withConnection {
  try {
    prepareStatement(sql, keys).use { it.block() }
  } catch (e: SQLException) {
    var message = e.message
    if (message?.contains("No hstore extension") == true) message += "\n  Probably you need to wrap some complex field into jsonb() or do other type conversion"
    throw if (message?.contains("unique constraint") == true) AlreadyExistsException(e)
          else SQLException("$message\n  SQL: $sql", e.sqlState, e.errorCode, e)
  }
}

fun <R> DataSource.withCall(@Language("SQL") sql: String, block: CallableStatement.() -> R): R = withConnection {
  prepareCall(sql).use { it.block() }
}

@Deprecated("Experimental API")
fun DataSource.call(callable: String, vararg parameters: Any?, returnSqlType: Int? = null): Any =
  withCall("{${returnSqlType?.let { "?=" } ?: ""}call $callable(${parameters.joinToString { placeholder(it) }})}") {
    var i = 1
    returnSqlType?.let { registerOutParameter(i++, it) }
    setAll(parameters.asSequence(), i)
    execute()
    (returnSqlType?.let { getObject(1) } ?: Unit)
  }

// TODO: add insert with mapper that returns the generated keys
fun DataSource.insert(@Language("SQL", prefix = selectFrom) table: String, values: ValueMap, suffix: String = "") =
  insertBatch(table, sequenceOf(values), suffix).first()

fun DataSource.insertBatch(@Language("SQL", prefix = selectFrom) table: String, values: Sequence<ValueMap>, suffix: String = ""): IntArray {
  val keyValuesToSet = values.map { it.filter { it.value !is GeneratedKey<*> } }
  val valuesToSet = keyValuesToSet.map { setValues(it) }
  val first = keyValuesToSet.firstOrNull() ?: return intArrayOf()
  val hasGeneratedKeys = first.size != values.first().size
  return execBatch(insertExpr(table, first) + suffix, valuesToSet, if (hasGeneratedKeys) RETURN_GENERATED_KEYS else NO_GENERATED_KEYS) {
    if (hasGeneratedKeys) processGeneratedKeys(values)
  }
}

// TODO: take uniqueFields as a Set
fun DataSource.upsert(@Language("SQL", prefix = selectFrom) table: String, values: ValueMap, uniqueFields: String = "id", where: Where = emptyList(), skipUpdateFields: Set<String> = setOf(uniqueFields)): Int =
  upsertBatch(table, sequenceOf(values), uniqueFields, where, skipUpdateFields).first()

// TODO: make it work per DataSource, use ConfigDataSource.isPostgres
internal val isPostgres = Config.optional("DB_URL")?.startsWith("jdbc:postgres") == true

fun DataSource.upsertBatch(@Language("SQL", prefix = selectFrom) table: String, values: Sequence<ValueMap>, uniqueFields: String = "id", where: Where = emptyList(), skipUpdateFields: Set<String> = setOf(uniqueFields)): IntArray {
  val where = whereConvert(where.map { (k, v) -> "$table.${q(name(k))}" to v })
  val first = values.firstOrNull() ?: return intArrayOf()
  val updateExpr = first.keys.asSequence().map { name(it) }.filter { it !in skipUpdateFields }
                   .joinToString { k -> q(k).let { "$it=excluded.$it" } }
  val whereValues = whereValues(where)
  val valuesToSet = values.map { setValues(it) + whereValues }
  val expr = if (isPostgres)
    insertExpr(table, first) + " on conflict ($uniqueFields) do update set ${updateExpr}${whereExpr(where)}"
  else """
    merge into ${q(table)} using (${valuesExpr(first)}) as excluded ${columnsExpr(first)}
      on ${uniqueFields.split(",").map { it.trim() }.joinToString(" and ") { "${q(table)}.$it = excluded.$it" }}
      when matched${whereExpr(where).replace("where", "and")} then update set $updateExpr
      when not matched then insert ${columnsExpr(first)} values (${first.keys.joinToString { "excluded." + q(name(it)) }});
    """
  return execBatch(expr, valuesToSet)
}

internal fun insertExpr(@Language("SQL", prefix = selectFrom) table: String, values: ValueMap) =
  "insert into ${q(table)} ${columnsExpr(values)} ${valuesExpr(values)}"

internal fun columnsExpr(values: ValueMap) = "(${values.keys.joinToString { q(name(it)) }})"
internal fun valuesExpr(values: ValueMap) = "values (${values.values.joinToString { placeholder(it) }})"

inline fun DataSource.update(@Language("SQL", prefix = selectFrom) table: String, values: ValueMap, vararg where: ColValue?): Int =
  update(table, values, where.filterNotNull())

fun DataSource.update(@Language("SQL", prefix = selectFrom) table: String, values: ValueMap, where: Where): Int = whereConvert(where).let { where ->
  exec("update ${q(table)} set ${setExpr(values)}${whereExpr(where)}", setValues(values) + whereValues(where))
}

inline fun DataSource.delete(@Language("SQL", prefix = selectFrom) table: String, vararg where: ColValue?): Int = delete(table, where.filterNotNull())

fun DataSource.delete(@Language("SQL", prefix = selectFrom) table: String, where: Where): Int = whereConvert(where).let { where ->
  exec("delete from ${q(table)}${whereExpr(where)}", whereValues(where))
}

private fun isEmptyCollection(v: Any?) = v is Collection<*> && v.isEmpty() || v is Array<*> && v.isEmpty()

internal fun whereConvert(where: Where) = where.map { (k, v) -> k to whereValueConvert(v) }
internal fun whereValueConvert(v: Any?) = if (isEmptyCollection(v)) emptyArray else when (v) {
  null -> isNull
  is Iterable<*> -> In(v)
  is Array<*> -> In(*v)
  is ClosedRange<*> -> Between(v)
  is OpenEndRange<*> -> BetweenExcl(v)
  else -> v
}

internal fun setExpr(values: ValueMap) = values.entries.map { (k, v) -> k to v }.join(", ")
internal fun whereExpr(where: Where) = if (where.isEmpty()) "" else " where " + where.join(" and ")

internal fun Iterable<ColValue>.join(separator: String) = joinToString(separator) { (k, v) ->
  val n = name(k)
  if (v is SqlExpr) v.expr(n) else q(n) + "=" + placeholder(v)
}

internal fun name(key: ColName) = when(key) {
  is KProperty1<*, *> -> key.colName
  is String -> key
  else -> throw UnsupportedOperationException("$key should be a KProperty1 or String")
}

val KProperty1<*, *>.colName get() = findAnnotation<Column>()?.name ?: name

internal fun q(name: String) = if (name in namesToQuote) "\"$name\"" else name

internal fun placeholder(v: Any?) = when {
  v is SqlExpr -> v.expr
  isEmptyCollection(v) -> emptyArray.expr
  v is Decimal -> if (isPostgres) "?::decimal" else "?"
  else -> "?"
}

internal fun setValues(values: ValueMap) = values.values.asSequence().flatMap { it.toIterable() }
internal fun whereValues(where: Where) = where.asSequence().map { it.second }.flatValues()
internal fun Sequence<Any?>.flatValues() = filterNotNull().flatMap { it.toIterable() }
private fun Any?.toIterable(): Iterable<Any?> = if (isEmptyCollection(this)) emptyList() else if (this is SqlExpr) values else listOf(this)

operator fun PreparedStatement.set(i: Int, value: Any?) {
  if (value is InputStream) setBinaryStream(i, value)
  else setObject(i, JdbcConverter.to(value, connection))
}

fun PreparedStatement.setAll(values: Sequence<Any?>, startIndex: Int = 1) {
  var i = startIndex
  values.forEach { v -> this[i++] = v }
}

var Connection.applicationName get() = getClientInfo("ApplicationName")
                               set(value) { setClientInfo("ApplicationName", value) }

inline fun <reified T: Wrapper> Wrapper.isWrapperFor(): Boolean = isWrapperFor(T::class.java)
inline fun <reified T: Wrapper> Wrapper.unwrap(): T = unwrap(T::class.java)
inline fun <T: Wrapper> Wrapper.unwrapOrNull(iface: Class<T>): T? = if (isWrapperFor(iface)) unwrap(iface) else null
inline fun <reified T: Wrapper> Wrapper.unwrapOrNull(): T? = unwrapOrNull(T::class.java)
