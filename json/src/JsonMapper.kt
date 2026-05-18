package klite.json

import org.intellij.lang.annotations.Language
import java.io.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.annotation.AnnotationTarget.PROPERTY
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

@Target(PROPERTY) annotation class JsonIgnore
@Target(PROPERTY) annotation class JsonProperty(val value: String = "", val readOnly: Boolean = false)

data class JsonMapper(
  val trimToNull: Boolean = true,
  val renderNulls: Boolean = false,
  val keys: KeyConverter = KeyConverter(),
  val values: ValueConverter<Any?> = ValueConverter()
) {
  fun <T> parse(json: Reader, type: KType?): T = JsonParser(json, this).readValue(type) as T
  fun <T> parse(@Language("JSON") json: String, type: KType?): T = parse(json.reader(), type) as T
  fun <T> parse(json: InputStream, type: KType?): T = parse(json.reader(), type) as T

  fun render(o: Any?, out: Writer) = JsonRenderer(out, this).render(o)
  fun render(o: Any?, out: OutputStream) = OutputStreamWriter(out).let { try { render(o, it) } finally { it.flush() } }
  @Language("JSON") fun render(o: Any?): String = FastStringWriter().also { render(o, it) }.toString()

  internal val inlineClassesAsString = ConcurrentHashMap<KClass<*>, Boolean>()
}

inline fun <reified T> JsonMapper.parse(json: Reader): T = parse(json, typeOf<T>())
inline fun <reified T> JsonMapper.parse(@Language("JSON") json: String): T = parse(json, typeOf<T>())
inline fun <reified T> JsonMapper.parse(json: InputStream): T = parse(json, typeOf<T>())

@Deprecated("Use klite.ValueConverter instead", ReplaceWith("klite.ValueConverter<T>"))
typealias ValueConverter<T> = klite.ValueConverter<T>
@Deprecated("Use klite.KeyConverter instead", ReplaceWith("klite.KeyConverter"))
typealias KeyConverter = klite.KeyConverter
@Deprecated("Use klite.SnakeCase instead", ReplaceWith("klite.SnakeCase"))
typealias SnakeCase = klite.SnakeCase
@Deprecated("Use klite.Capitalize instead", ReplaceWith("klite.Capitalize"))
typealias Capitalize = klite.Capitalize

class FastStringWriter: Writer() {
  private val buf = StringBuilder()
  override fun close() {}
  override fun flush() {}
  override fun toString() = buf.toString()

  override fun write(c: Int) { buf.append(c.toChar()) }
  override fun write(s: String) { buf.append(s) }
  override fun write(cbuf: CharArray, off: Int, len: Int) { buf.append(cbuf, off, len) }
}
