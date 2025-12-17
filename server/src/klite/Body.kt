package klite

import java.io.InputStream
import java.io.OutputStream
import kotlin.reflect.KClass
import kotlin.reflect.KType

interface SupportsContentType {
  val contentType: String
}

// TODO: support multiple content types with quality factors: application/openmetrics-text;version=1.0.0;escaping=allow-utf-8;q=0.6,application/openmetrics-text;version=0.0.1;q=0.5,text/plain;version=1.0.0;escaping=allow-utf-8;q=0.4,text/plain;version=0.0.4;q=0.3,*/*;q=0.2
class Accept(val contentTypes: String?) {
  val isRelaxed get() = contentTypes == null || contentTypes.contains("*") || contentTypes.startsWith("text/html,")
  operator fun invoke(contentType: String) = contentTypes?.contains(contentType) ?: true
  operator fun invoke(provider: SupportsContentType) = invoke(provider.contentType)
}

interface BodyRenderer: SupportsContentType {
  fun render(output: OutputStream, value: Any?) { throw UnsupportedOperationException() }
  fun render(e: HttpExchange, code: StatusCode, value: Any?) {
    e.startResponse(code, contentType = contentType).use { render(it, value) }
  }
}

interface BodyParser: SupportsContentType {
  @Deprecated("Use/implement the KType method")
  fun <T: Any> parse(input: InputStream, type: KClass<T>): T = throw UnsupportedOperationException()
  @Suppress("UNCHECKED_CAST")
  fun <T: Any> parse(input: InputStream, type: KType): T = parse(input, type.classifier as KClass<T>)
  fun <T> parse(e: HttpExchange, type: KType): T = e.requestStream.use { parse(it, type) }
}

class TextBodyRenderer(override val contentType: String = MimeTypes.text): BodyRenderer {
  override fun render(output: OutputStream, value: Any?) = output.write(value.toString())
}

class TextBodyParser(override val contentType: String = MimeTypes.text): BodyParser {
  @Suppress("UNCHECKED_CAST")
  override fun <T: Any> parse(input: InputStream, type: KType): T {
    val s = input.reader().readText()
    return if (type == String::class) s as T else Converter.from(s, type)
  }
}

class TextBody(
  override val contentType: String = MimeTypes.text,
  private val renderer: TextBodyRenderer = TextBodyRenderer(contentType),
  private val parser: TextBodyParser = TextBodyParser(contentType)
): BodyRenderer by renderer, BodyParser by parser

class FormUrlEncodedParser(override val contentType: String = MimeTypes.wwwForm): BodyParser {
  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> parse(input: InputStream, type: KType): T = urlDecodeParams(input.reader().readText()) as T
}
