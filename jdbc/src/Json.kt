package klite.jdbc

import klite.json.JsonMapper
import klite.json.parse
import java.sql.ResultSet

var dbJsonMapper = JsonMapper()

fun jsonb(value: String?) = SqlComputed("?::jsonb", value)
fun jsonb(value: Any?) = jsonb(dbJsonMapper.render(value))

inline fun <reified T: Any> ResultSet.getJsonOrNull(column: String): T? = getString(column)?.let { dbJsonMapper.parse<T>(it) }
inline fun <reified T: Any> ResultSet.getJson(column: String): T = getJsonOrNull(column) ?: error("$column is null")
