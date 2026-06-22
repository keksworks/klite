package klite.jdbc

import klite.json.JsonMapper
import java.sql.ResultSet
import kotlin.reflect.typeOf

var dbJsonMapper = JsonMapper()

fun jsonb(value: String?) = SqlComputed("?::jsonb", value)
fun jsonb(value: Any?) = jsonb(dbJsonMapper.render(value))

fun <T> ResultSet.getJsonOrNull(column: String, type: kotlin.reflect.KType): T? =
  getString(column)?.let { dbJsonMapper.parse(it, type) as T }

inline fun <reified T: Any> ResultSet.getJsonOrNull(column: String): T? = getJsonOrNull<T>(column, typeOf<T>())
inline fun <reified T: Any> ResultSet.getJson(column: String): T = getJsonOrNull(column) ?: error("$column is null")
