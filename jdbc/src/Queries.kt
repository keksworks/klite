package klite.jdbc

import org.intellij.lang.annotations.Language
import java.sql.ResultSet
import javax.sql.DataSource

internal const val selectFrom = "select * from "
internal const val selectFromTable = "$selectFrom table "
internal const val selectWhere = "$selectFromTable where "

typealias Mapper<R> = ResultSet.() -> R
typealias ColName = Any // String | KProperty1
typealias ColValue = Pair<ColName, Any?>

typealias Where = Collection<ColValue>
typealias ValueMap = Map<out ColName, *>

@Deprecated(replaceWith = ReplaceWith("ValueMap"), message = "Use ValueMap instead")
typealias Values = ValueMap

fun <R, ID> DataSource.select(@Language("SQL", prefix = selectFrom) table: String, id: ID, column: String = "id", @Language("SQL", prefix = selectFromTable) suffix: String = "", mapper: Mapper<R>): R =
  selectSeq(table, listOf(column to id), suffix, mapper).firstOrNull() ?: throw NoSuchElementException("${table.substringBefore(" ")}:$id not found")

internal fun <R> DataSource.selectSeq(@Language("SQL", prefix = selectFrom) table: String, where: Where = emptyList(), @Language("SQL", prefix = selectFromTable) suffix: String = "", mapper: Mapper<R>): Sequence<R> =
  select(table).where(where).suffix(suffix).map(mapper)

@Deprecated("use selectSeq instead", replaceWith = ReplaceWith("selectSeq(table, where, suffix, mapper).toCollection(into)"))
fun <R, C: MutableCollection<R>> DataSource.select(@Language("SQL", prefix = selectFrom) table: String, where: Where = emptyList(), @Language("SQL", prefix = selectFromTable) suffix: String = "", into: C, mapper: Mapper<R>): C =
  selectSeq(table, where, suffix, mapper).toCollection(into)

inline fun <R> DataSource.select(@Language("SQL", prefix = selectFrom) table: String, vararg where: ColValue?, @Language("SQL", prefix = selectFromTable) suffix: String = "", noinline mapper: Mapper<R>): List<R> =
  select(table, where.filterNotNull(), suffix, mapper = mapper)

fun <R> DataSource.select(@Language("SQL", prefix = selectFrom) table: String, where: Where, @Language("SQL", prefix = selectFromTable) suffix: String = "", mapper: Mapper<R>) =
  select(table, where, suffix, mutableListOf(), mapper) as List<R>

inline fun <reified R> DataSource.select(@Language("SQL", prefix = selectFrom) table: String, where: Where, @Language("SQL", prefix = selectFromTable) suffix: String = ""): List<R> =
  select(table, where, suffix = suffix) { create() }

inline fun <reified R> DataSource.select(@Language("SQL", prefix = selectFrom) table: String, vararg where: ColValue?, @Language("SQL", prefix = selectFromTable) suffix: String = ""): List<R> =
  select(table, *where, suffix = suffix) { create() }

internal fun <R> DataSource.querySeq(@Language("SQL") select: String, where: Where = emptyList(), @Language("SQL", prefix = selectFromTable) suffix: String = "", mapper: Mapper<R>): Sequence<R> =
  query(select).where(where).suffix(suffix).map(mapper)

@Deprecated("use querySeq instead", replaceWith = ReplaceWith("querySeq(select, where, suffix, mapper).toCollection(into)"))
fun <R, C: MutableCollection<R>> DataSource.query(@Language("SQL") select: String, where: Where = emptyList(), @Language("SQL", prefix = selectFromTable) suffix: String = "", into: C, mapper: Mapper<R>): C =
  querySeq(select, where, suffix, mapper).toCollection(into)

inline fun <R> DataSource.query(@Language("SQL") select: String, vararg where: ColValue?, @Language("SQL", prefix = selectFromTable) suffix: String = "", noinline mapper: Mapper<R>): List<R> =
  query(select, where.filterNotNull(), suffix, mapper = mapper)

fun <R> DataSource.query(@Language("SQL") select: String, where: Where, @Language("SQL", prefix = selectFromTable) suffix: String = "", mapper: Mapper<R>) =
  querySeq(select, where, suffix, mapper).toList()

inline fun <reified R> DataSource.query(@Language("SQL") select: String, where: Where, @Language("SQL", prefix = selectFromTable) suffix: String = ""): List<R> =
  query(select, where, suffix = suffix) { create() }

inline fun <reified R> DataSource.query(@Language("SQL") select: String, vararg where: ColValue?, @Language("SQL", prefix = selectFromTable) suffix: String = ""): List<R> =
  query(select, *where, suffix = suffix) { create() }

fun DataSource.count(@Language("SQL", prefix = selectFrom) table: String, where: Where = emptyList()) = querySeq("select count(*) from $table", where) { getLong(1) }.first()
