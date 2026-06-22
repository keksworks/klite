package klite.jdbc

import klite.*
import java.sql.ResultSet
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation

inline fun <reified T: Any> ResultSet.create(vararg provided: PropValue<T, *>) = create(T::class, *provided)

/** Take only prefixed column names, e.g. "alias.id" to get second joined table, see [populatePgColumnNameIndex] for details */
inline fun <reified T: Any> ResultSet.create(columnPrefix: String, vararg provided: PropValue<T, *>) = create(T::class, *provided, columnPrefix = columnPrefix)

fun <T: Any> ResultSet.create(type: KClass<T>, vararg provided: PropValue<T, *>, columnPrefix: String = ""): T {
  val extraArgs = provided.associate { it.first.name to it.second }
  return type.create {
    val prop = type.publicProperties[it.name]
    val column = columnPrefix + (prop?.colName ?: it.name)
    if (extraArgs.containsKey(it.name)) extraArgs[it.name!!]
    else if (prop?.findAnnotation<JsonColumn>() != null) getJsonOrNull(column, it.type)
    else if (it.isOptional) getOptional<T>(column, it.type).getOrDefault(AbsentValue)
    else get(column, it.type)
  }
}

fun <E: Any> E.toDBValues(): Map<KProperty1<E, *>, Any?> = toValues().mapValues { (k, v) ->
  if (k.findAnnotation<JsonColumn>() != null) jsonb(v) else v
}
