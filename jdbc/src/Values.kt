package klite.jdbc

import klite.*
import java.sql.ResultSet
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation

private data class AnnotationMeta(val isJson: Boolean, val isFlatten: Boolean) {
  val some get() = isJson || isFlatten
}
private val annotationMetaCache = ConcurrentHashMap<KClass<*>, Map<String, AnnotationMeta>>()

private fun KClass<*>.annotationMeta() = annotationMetaCache[this] ?: publicProperties.mapValues { (_, p) ->
  AnnotationMeta(p.findAnnotation<JsonColumn>() != null, p.findAnnotation<FlattenColumns>() != null)
}.filter { it.value.some }.also { annotationMetaCache[this] = it }

inline fun <reified T: Any> ResultSet.create(vararg provided: PropValue<T, *>) = create(T::class, *provided)

/** Take only prefixed column names, e.g. "alias.id" to get second joined table, see [populatePgColumnNameIndex] for details */
inline fun <reified T: Any> ResultSet.create(columnPrefix: String, vararg provided: PropValue<T, *>) = create(T::class, *provided, columnPrefix = columnPrefix)

fun <T: Any> ResultSet.create(type: KClass<T>, vararg provided: PropValue<T, *>, columnPrefix: String = ""): T {
  val extraArgs = provided.associate { it.first.name to it.second }
  val meta = type.annotationMeta()
  return type.create {
    val prop = type.publicProperties[it.name]
    val column = columnPrefix + (prop?.colName ?: it.name)
    if (extraArgs.containsKey(it.name)) extraArgs[it.name!!]
    else if (prop != null && meta[it.name]?.isJson == true) getJsonOrNull(column, it.type)
    else if (prop != null && meta[it.name]?.isFlatten == true) create(it.type.classifier as KClass<T>, *provided, columnPrefix = columnPrefix)
    else if (it.isOptional) getOptional<T>(column, it.type).getOrDefault(AbsentValue)
    else get(column, it.type)
  }
}

fun <T: Any> T.toDBValues(vararg provided: PropValue<T, *>, skip: Collection<KProperty1<T, *>> = emptyList()): Map<KProperty1<T, *>, Any?> {
  val values = toValues(*provided, skip = skip) as MutableMap
  val meta = this::class.annotationMeta()
  if (meta.isEmpty()) return values
  values.entries.toList().forEach { (prop, v) ->
    if (meta[prop.name]?.isJson == true) values[prop] = jsonb(v)
    else if (v != null && meta[prop.name]?.isFlatten == true) {
      values.remove(prop)
      values += v.toDBValues() as Map<KProperty1<T, *>, Any?>
    }
  }
  return values
}
