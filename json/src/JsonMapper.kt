package klite.json

import klite.KeyConverter
import klite.ValueConverter
import org.intellij.lang.annotations.Language
import java.io.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.annotation.AnnotationTarget.PROPERTY
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

@Target(PROPERTY) annotation class JsonIgnore
@Target(PROPERTY) annotation class JsonProperty(val value: String = "", val readOnly: Boolean = false)
@Target(PROPERTY) annotation class JsonSubTypes(val key: String = "type", val types: Array<Type> = []) {
  annotation class Type(val value: String, val type: KClass<*>)
}

const val bufSize = 8192

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
  fun render(o: Any?, out: OutputStream) = render(o, OutputStreamWriter(out))
  @Language("JSON") fun render(o: Any?): String = StringWriter().also { render(o, it) }.toString()

  internal val inlineClassesAsString = ConcurrentHashMap<KClass<*>, Boolean>()
}

inline fun <reified T> JsonMapper.parse(json: Reader): T = parse(json, typeOf<T>())
inline fun <reified T> JsonMapper.parse(@Language("JSON") json: String): T = parse(json, typeOf<T>())
inline fun <reified T> JsonMapper.parse(json: InputStream): T = parse(json, typeOf<T>())
