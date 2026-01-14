package klite.jdbc

import klite.Decimal
import klite.d
import klite.uuid
import java.sql.ResultSet
import java.time.Instant
import java.time.Period
import kotlin.reflect.KType
import kotlin.reflect.typeOf

@Suppress("UNCHECKED_CAST")
fun <T> ResultSet.get(column: String, type: KType): T = JdbcConverter.from(when (type.classifier) {
  Int::class -> getIntOrNull(column)
  Decimal::class -> getDecimalOrNull(column)
  Instant::class -> getTimestamp(column)
  else -> getObjectUnwrapped(column)
}, type) as T

inline operator fun <reified T> ResultSet.get(column: String): T = get(column, typeOf<T>())

fun <T> ResultSet.getOptional(column: String, type: KType): Result<T> = runCatching { get(column, type) }
inline fun <reified T> ResultSet.getOptional(column: String): Result<T> = getOptional(column, typeOf<T>())

fun ResultSet.getObjectUnwrapped(column: String): Any? {
  val o = getObject(column)
  return if (o != null && o.javaClass.simpleName == "PGobject") o.javaClass.getMethod("getValue").invoke(o)
         else o
}

fun ResultSet.getUuid(column: String = "id") = getString(column).uuid
fun ResultSet.getUuidOrNull(column: String = "id") = getString(column)?.uuid

fun ResultSet.getInstant(column: String) = getTimestamp(column).toInstant()
fun ResultSet.getInstantOrNull(column: String) = getTimestamp(column)?.toInstant()
fun ResultSet.getLocalDate(column: String) = getDate(column).toLocalDate()
fun ResultSet.getLocalDateOrNull(column: String) = getDate(column)?.toLocalDate()
fun ResultSet.getLocalDateTime(column: String) = getTimestamp(column).toLocalDateTime()
fun ResultSet.getLocalDateTimeOrNull(column: String) = getTimestamp(column)?.toLocalDateTime()
fun ResultSet.getLocalTime(column: String) = getTime(column).toLocalTime()
fun ResultSet.getLocalTimeOrNull(column: String) = getTime(column)?.toLocalTime()
fun ResultSet.getPeriod(column: String) = Period.parse(getString(column))
fun ResultSet.getPeriodOrNull(column: String) = getString(column)?.let { Period.parse(it) }
fun ResultSet.getDecimal(column: String) = getString(column).d

fun ResultSet.getIntOrNull(column: String) = getInt(column).takeUnless { wasNull() }
fun ResultSet.getLongOrNull(column: String) = getLong(column).takeUnless { wasNull() }
fun ResultSet.getFloatOrNull(column: String) = getFloat(column).takeUnless { wasNull() }
fun ResultSet.getDoubleOrNull(column: String) = getDouble(column).takeUnless { wasNull() }
fun ResultSet.getDecimalOrNull(column: String) = getString(column)?.d

inline fun <reified T: Enum<T>> ResultSet.getEnum(column: String) = enumValueOf<T>(getString(column))
inline fun <reified T: Enum<T>> ResultSet.getEnumOrNull(column: String) = getString(column)?.let { enumValueOf<T>(it) }
