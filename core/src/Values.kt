@file:Suppress("UNCHECKED_CAST")
package klite

import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.*
import kotlin.reflect.KVisibility.PUBLIC
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaField

private val publicPropsCache = ConcurrentHashMap<KClass<*>, Map<String, KProperty1<*, *>>>()

val <T: Any> KClass<T>.publicProperties get() = publicPropsCache.getOrPut(this) {
  memberProperties.filter { it.visibility == PUBLIC }.associateBy { it.name }
} as Map<String, KProperty1<T, *>>

val <T: Any> T.publicProperties get() = this::class.publicProperties.values.asSequence() as Sequence<KProperty1<T, *>>

typealias PropValue<T, V> = Pair<KProperty1<T, V>, V>

fun <T: Any> T.toValues(provided: Map<KProperty1<T, *>, Any?> = emptyMap(), skip: Set<String> = emptySet()) =
  toValues(publicProperties.filter { it.javaField != null }.filter { it.name !in skip }, provided)

fun <T: Any> T.toValues(props: Sequence<KProperty1<T, *>>, provided: Map<KProperty1<T, *>, Any?> = emptyMap()) =
  props.associateWith { provided[it] ?: it.valueOf(this) }

fun <T: Any> T.toValues(vararg provided: PropValue<T, *>, skip: Collection<KProperty1<T, *>> = emptySet()) =
  toValues(provided.toMap(), if (skip.isEmpty()) emptySet() else skip.map { it.name }.toSet())

fun <T: Any> T.toValuesSkipping(vararg skip: KProperty1<T, *>) =
  toValues(skip = skip.map { it.name }.toSet())

fun <T> KProperty1<T, *>.valueOf(o: T) = try {
  val v = get(o)
  if (v != null && !v::class.java.isSynthetic && v::class.isValue && v.unboxInline() == null) null else v // workaround for a bug in kotlin-reflect: https://youtrack.jetbrains.com/issue/KT-57590
} catch (e: InvocationTargetException) { throw e.targetException }

val classCreators = ConcurrentHashMap<KClass<*>, KFunction<*>>()

fun <T: Any> KClass<T>.create(valueOf: (KParameter) -> Any?): T {
  val creator = classCreators.getOrPut(this) { primaryConstructor ?: error("$this does not have primary constructor") } as KFunction<T>
  val args = HashMap<KParameter, Any?>()
  creator.parameters.forEach {
    val v = valueOf(it)
    if (v == AbsentValue) {
      if (it.type.isMarkedNullable && !it.isOptional) args[it] = null
    } else if (v == null) {
      if (it.type.isMarkedNullable) args[it] = null
    } else args[it] = v
  }
  return try {
    creator.callBy(args)
  } catch (e: InvocationTargetException) {
    throw e.targetException
  } catch (e: IllegalArgumentException) {
    throw IllegalArgumentException("Cannot create $simpleName from " + args.mapKeys { it.key.name } + ": " +
      e.message?.replace(".*required parameter: parameter #\\d+ (.*) of.*".toRegex(), "missing $1"), e)
  }
}

// out Any = String | KProperty1<*>
fun KType.createFrom(values: Map<out Any, Any?>) = (classifier as KClass<*>).createFrom(values)
inline fun <reified T: Any> Map<out Any, Any?>.create() = T::class.createFrom(this)

fun <T: Any> KClass<T>.createFrom(values: Map<out Any, Any?>) = create {
  var v = values.getOrDefault(it.name!!, AbsentValue)
  if (v == AbsentValue) publicProperties[it.name]?.let { v = values.getOrDefault(it, AbsentValue) }
  Converter.from(v, it.type)
}

object AbsentValue
