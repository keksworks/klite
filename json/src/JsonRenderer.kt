package klite.json

import klite.*
import java.io.Writer
import java.util.AbstractMap.SimpleImmutableEntry
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.superclasses

class JsonRenderer(private val out: Writer, private val opts: JsonMapper): AutoCloseable {
  fun render(o: Any?) = writeValue(o)

  @Suppress("NAME_SHADOWING")
  private fun writeValue(o: Any?, prop: KProperty1<*, *>? = null) {
    when (val o = opts.values.to(o)) {
      is CharSequence -> writeString(o.toString())
      is Iterable<*> -> writeArray(o.iterator())
      is Sequence<*> -> writeArray(o.iterator())
      is Array<*> -> writeArray(o.iterator())
      is Map<*, *> -> writeObjectEntries(o.asSequence())
      null, is Number, is Boolean -> write(o.toString())
      else ->
        if (o::class.isValue && o::class.hasAnnotation<JvmInline>() && !inlineAsString(o)) writeValue(o.unboxInline())
        else if (Converter.supports(o::class)) writeString(o.toString())
        else writeObject(o, prop)
    }
  }

  private fun inlineAsString(o: Any): Boolean = opts.inlineClassesAsString.getOrPut(o::class) {
    !o.toString().let { it.startsWith(o::class.simpleName!!) && it.endsWith(')') } &&
      o::class.constructors.any { it.parameters.size == 1 && it.parameters.first().type.classifier == String::class }
  }

  private fun writeString(s: String) {
    write('\"')
    for (i in s.indices) when (val c = s[i]) {
      '\n' -> write("\\n"); '\r' -> write("\\r"); '\t' -> write("\\t"); '"' -> write("\\\""); '\\' -> write("\\\\")
      in '\u0000'..'\u001F' -> { write("\\u"); write(c.code.toString(16).padStart(4, '0')) }
      else -> write(c)
    }
    write('\"')
  }

  private fun writeArray(i: Iterator<*>) {
    write('[')
    if (i.hasNext()) writeValue(i.next())
    i.forEach { write(','); writeValue(it) }
    write(']')
  }

  private fun writeObjectEntries(entries: Sequence<Map.Entry<Any?, Any?>>) {
    val i = (if (opts.renderNulls) entries else entries.filter { it.value != null }).iterator()
    write('{')
    if (i.hasNext()) writeEntry(i.next())
    i.forEach { write(','); writeEntry(it) }
    write('}')
  }

  private fun writeObject(o: Any, prop: KProperty1<*, *>? = null) {
    val subTypes = prop?.findAnnotation<JsonSubTypes>() ?: findSubTypesAnnotation(o::class)
    var entries = o.publicProperties.notIgnored.map { SimpleImmutableEntry(it, it.valueOf(o)) } as Sequence<Map.Entry<Any, Any?>>
    if (subTypes != null)
      entries = sequenceOf(SimpleImmutableEntry(subTypes.key, findDiscriminatorValue(o::class, subTypes))) + entries
    writeObjectEntries(entries)
  }

  private fun findSubTypesAnnotation(cls: KClass<*>): JsonSubTypes? {
    var current: KClass<*>? = cls
    while (current != null && current != Any::class) {
      current.findAnnotation<JsonSubTypes>()?.let { return it }
      current = current.superclasses.firstOrNull()
    }
    return null
  }

  private fun findDiscriminatorValue(cls: KClass<*>, subTypes: JsonSubTypes): String? {
    val types = subTypes.types
    return if (types.isNotEmpty()) { types.find { it.type == cls }?.value ?: cls.simpleName } else cls.simpleName
  }

  private fun writeEntry(it: Map.Entry<Any?, Any?>) {
    val key = (it.key as? KProperty1<Any, *>)?.jsonName ?: it.key.toString()
    writeString(opts.keys.to(key))
    write(':')
    writeValue(it.value, it.key as? KProperty1<Any, *>)
  }

  private fun write(c: Char) = out.write(c.code)
  private fun write(s: String) = out.write(s)

  override fun close() = out.close()
}

internal val <T: Any> Sequence<KProperty1<T, *>>.notIgnored get() = filter { !it.hasAnnotation<JsonIgnore>() }
internal val KProperty1<*, *>.jsonName get() = findAnnotation<JsonProperty>()?.value?.trimToNull() ?: name

fun <T: Any> T.toJsonValues(vararg provided: PropValue<T, *>, skip: Set<KProperty1<T, *>> = emptySet()) =
  toValues(publicProperties.notIgnored - skip, provided.toMap())
