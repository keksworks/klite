package klite.json

import klite.*
import java.io.Writer
import java.util.*
import java.util.AbstractMap.SimpleImmutableEntry
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation

class JsonRenderer(private val out: Writer, private val opts: JsonMapper): AutoCloseable {
  fun render(o: Any?) = writeValue(o)

  @Suppress("NAME_SHADOWING")
  private fun writeValue(o: Any?) {
    when (val o = opts.values.to(o)) {
      is CharSequence -> writeString(o.toString())
      is Iterable<*> -> writeArray(o)
      is Array<*> -> writeArray(Arrays.asList(*o))
      is Map<*, *> -> writeObjectEntries(o.asSequence())
      null, is Number, is Boolean -> write(o.toString())
      else ->
        if (o::class.isValue && o::class.hasAnnotation<JvmInline>() && !inlineAsString(o)) writeValue(o.unboxInline())
        else if (Converter.supports(o::class)) writeString(o.toString())
        else writeObject(o)
    }
  }

  private val inlineClassesAsString = ConcurrentHashMap<KClass<*>, Boolean>()
  private fun inlineAsString(o: Any): Boolean = inlineClassesAsString.getOrPut(o::class) {
    val s = o.toString()
    o.unboxInline().toString() != s && !(s.startsWith(o::class.simpleName!!) && s.endsWith(')'))
  }

  private fun writeString(s: String) {
    write('\"')
    s.forEach { when(it) {
      '\n' -> write("\\n"); '\r' -> write("\\r"); '\t' -> write("\\t"); '"' -> write("\\\""); '\\' -> write("\\\\")
      in '\u0000'..'\u001F' -> { write("\\u"); write(it.code.toString(16).padStart(4, '0')) }
      else -> write(it)
    } }
    write('\"')
  }

  private fun writeArray(o: Iterable<*>) {
    write('[')
    val i = o.iterator()
    if (i.hasNext()) writeValue(i.next())
    i.forEachRemaining { write(','); writeValue(it) }
    write(']')
  }

  private fun writeObjectEntries(entries: Sequence<Map.Entry<Any?, Any?>>) {
    val i = (if (opts.renderNulls) entries else entries.filter { it.value != null }).iterator()
    write('{')
    if (i.hasNext()) writeEntry(i.next())
    i.forEachRemaining { write(','); writeEntry(it) }
    write('}')
  }

  private fun writeObject(o: Any) = writeObjectEntries(o.publicProperties.notIgnored
    .map { SimpleImmutableEntry(it.jsonName, it.valueOf(o)) })

  private fun writeEntry(it: Map.Entry<Any?, Any?>) {
    val key = (it.key as? KProperty1<Any, *>)?.jsonName ?: it.key.toString()
    writeString(opts.keys.to(key))
    write(':')
    writeValue(it.value)
  }

  private fun write(c: Char) = out.write(c.code)
  private fun write(s: String) = out.write(s)

  override fun close() = out.close()
}

internal val <T: Any> Sequence<KProperty1<T, *>>.notIgnored get() = filter { !it.hasAnnotation<JsonIgnore>() }
internal val KProperty1<*, *>.jsonName get() = findAnnotation<JsonProperty>()?.value?.trimToNull() ?: name

fun <T: Any> T.toJsonValues(vararg provided: PropValue<T, *>, skip: Collection<KProperty1<T, *>> = emptySet()) =
  toValues(publicProperties.notIgnored - skip, provided.toMap())
