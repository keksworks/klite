package klite.jdbc

import klite.TSID
import klite.ValueConverter
import klite.json.JsonMapper
import java.sql.ResultSet
import kotlin.reflect.KType
import kotlin.reflect.typeOf

var dbJsonMapper = JsonMapper(values = object: ValueConverter<Any?>() {
  override fun to(o: Any?) = (o as? TSID<*>)?.value ?: o
  override fun from(o: Any?, type: KType?) = if (type?.classifier == TSID::class && o !is TSID<*>) {
    val decimal = (o as? Number)?.toLong() ?: o?.toString()?.toLongOrNull()
    if (decimal != null) TSID<Any>(decimal) else TSID<Any>(o as String)
  } else o
})

fun jsonb(value: String?) = SqlComputed("?::jsonb", value)
fun jsonb(value: Any?) = jsonb(value?.let { dbJsonMapper.render(it) })

fun <T> ResultSet.getJsonOrNull(column: String, type: kotlin.reflect.KType): T? =
  getString(column)?.let { dbJsonMapper.parse(it, type) as T }

inline fun <reified T: Any> ResultSet.getJsonOrNull(column: String): T? = getJsonOrNull<T>(column, typeOf<T>())
inline fun <reified T: Any> ResultSet.getJson(column: String): T = getJsonOrNull(column) ?: error("$column is null")
