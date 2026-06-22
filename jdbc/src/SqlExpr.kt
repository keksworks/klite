package klite.jdbc

import org.intellij.lang.annotations.Language
import kotlin.reflect.KProperty1

open class SqlExpr(@Language("SQL", prefix = selectWhere) internal val expr: String, val values: Iterable<*> = emptyList<Any>()) {
  constructor(@Language("SQL", prefix = selectWhere) expr: String, vararg values: Any?): this(expr, values.toList())
  open fun expr(key: String) = expr
  override fun equals(other: Any?) = other === this || other is SqlExpr && other.expr == this.expr && other.values == this.values
  override fun hashCode() = expr.hashCode() + values.hashCode()
}

class SqlComputed(expr: String, vararg values: Any?): SqlExpr(expr, *values) {
  override fun expr(key: String) = q(key) + "=" + expr
}

open class SqlOp(val operator: String, values: Iterable<*>): SqlExpr(operator, values) {
  constructor(operator: String, value: Any?): this(operator, listOf(value))
  constructor(operator: String): this(operator, emptyList<Any>())
  override fun expr(key: String) = q(key) + " $operator" +
    if ((values as? Collection)?.isEmpty() == true) "" else " " + placeholder(values.first())
}

class NullOrOp(operator: String, value: Any?): SqlOp(operator, value) {
  override fun expr(key: String) = "(${q(key)} is null or ${q(key)} $operator ?)"
}

val isNull = SqlOp("is null")
val notNull = SqlOp("is not null")
val emptyArray = SqlComputed("'{}'")

infix fun String.eq(value: Any) = this to value
infix fun String.neq(value: Any) = this to SqlOp("!=", value)
infix fun String.distinct(value: Any?) = this to SqlOp("is distinct from", value)
infix fun String.notDistinct(value: Any?) = this to SqlOp("is not distinct from", value)
infix fun String.gt(value: Any) = this to SqlOp(">", value)
infix fun String.gte(value: Any) = this to SqlOp(">=", value)
infix fun String.lt(value: Any) = this to SqlOp("<", value)
infix fun String.lte(value: Any) = this to SqlOp("<=", value)
infix fun String.like(value: String) = this to SqlOp("like", value)
infix fun String.ilike(value: String) = this to SqlOp("ilike", value)
infix fun String.any(value: Any) = this to SqlExpr("?=any(${q(this)})", value)

infix fun <T, V> KProperty1<T, V>.eq(value: V) = this to value
infix fun <T, V> KProperty1<T, V>.neq(value: V) = this to SqlOp("!=", value)
infix fun <T, V> KProperty1<T, V>.distinct(value: V) = this to SqlOp("is distinct from", value)
infix fun <T, V> KProperty1<T, V>.notDistinct(value: V) = this to SqlOp("is not distinct from", value)
infix fun <T, V> KProperty1<T, V>.gt(value: V) = this to SqlOp(">", value)
infix fun <T, V> KProperty1<T, V>.gte(value: V) = this to SqlOp(">=", value)
infix fun <T, V> KProperty1<T, V>.lt(value: V) = this to SqlOp("<", value)
infix fun <T, V> KProperty1<T, V>.lte(value: V) = this to SqlOp("<=", value)
infix fun <T, V> KProperty1<T, V>.like(value: String) = this to SqlOp("like", value)
infix fun <T, V> KProperty1<T, V>.ilike(value: String) = this to SqlOp("ilike", value)
infix fun <T, V> KProperty1<T, V>.any(value: V) = this to SqlExpr("?=any(${q(name)})", value)
infix fun <T, V: Comparable<V>> KProperty1<T, V>.between(range: ClosedRange<V>) = this to Between(range.start, range.endInclusive)
infix fun <T, V: Comparable<V>> KProperty1<T, V>.between(range: OpenEndRange<V>) = this to BetweenExcl(range.start, range.endExclusive)
infix fun <T, E, V: Collection<E>> KProperty1<T, V>.`in`(values: V) = this to In(values)
infix fun <T, E, V: Collection<E>> KProperty1<T, V>.notIn(values: V) = this to NotIn(values)

class Between(from: Comparable<*>, to: Comparable<*>): SqlExpr("", from, to) {
  constructor(range: ClosedRange<*>): this(range.start, range.endInclusive)
  override fun expr(key: String) = q(key) + " between ? and ?"
}

class BetweenExcl(from: Comparable<*>, to: Comparable<*>): SqlExpr("", from, to) {
  constructor(range: OpenEndRange<*>): this(range.start, range.endExclusive)
  override fun expr(key: String) = "${q(key)} >= ? and ${q(key)} < ?"
}

open class In(values: Iterable<*>): SqlExpr("", values) {
  constructor(vararg values: Any?): this(values.toList())
  override fun expr(key: String) = q(key) + " in (${values.joinToString { "?" }})"
}

class NotIn(values: Iterable<*>): In(values) {
  constructor(vararg values: Any?): this(values.toList())
  override fun expr(key: String) = super.expr(key).replace(" in ", " not in ")
}

private fun seqExpr(where: Array<out Pair<ColName, Any?>?>, separator: String): SqlExpr {
  val converted = where.filterNotNull().map { (k, v) -> k to whereValueConvert(v) }
  return SqlExpr("(" + converted.join(separator) + ")", converted.map { it.second }.flatValues())
}

fun orExpr(vararg where: Pair<ColName, Any?>?) = seqExpr(where, " or ")
fun andExpr(vararg where: Pair<ColName, Any?>?) = seqExpr(where, " and ")

fun <K: ColName> or(vararg where: Pair<K, Any?>?) = where[0]!!.first to orExpr(*where)
fun <K: ColName> and(vararg where: Pair<K, Any?>?) = where[0]!!.first to andExpr(*where)

@Suppress("UNCHECKED_CAST")
fun <K: ColName> sql(@Language("SQL", prefix = selectWhere) expr: String, vararg values: Any?): Pair<K, SqlExpr> = (expr as K) to SqlExpr(expr, *values)
