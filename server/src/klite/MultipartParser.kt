package klite

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.*
import kotlin.reflect.KType

@Deprecated("Use MultipartParser")
typealias MultipartFormDataParser = MultipartParser
typealias FormDataParser = MultipartParser

class MultipartParser(
  override val contentType: String = MimeTypes.formData,
  private val nameHeader: String = "content-id" // e.g. SOAP/eDelivery support
): BodyParser {
  @Suppress("UNCHECKED_CAST")
  override fun <T: Any> parse(input: InputStream, type: KType) = parse(input) as T

  fun parse(input: InputStream): Map<String, Any> {
    var boundary: TrimmableOutputStream? = null
    while (boundary?.isEmpty() != false) boundary = input.readLine()!!.trimEnd()
    val result = mutableMapOf<String, Any>()
    var state = State()
    while (true) {
      val line = input.readLine() ?: break
      if (state.readingHeaders) {
        line.trimEnd()
        if (line.isEmpty()) { state.readingHeaders = false; continue }
        val (header, value) = line.toString(MimeTypes.textCharset).split(':', limit = 2)
        if (header.equals("content-type", ignoreCase = true)) {
          state.contentType = value.trim()
        } else if (header.equals("content-disposition", ignoreCase = true)) {
          val disposition = value.trim()
          val params = disposition.split(';').associate(::keyValue)
          state.name = params["name"]
          state.fileName = params["filename"]
        } else if (header.equals(nameHeader, ignoreCase = true)) {
          state.name = value.trim()
        }
      } else if (line.startsWith(boundary)) {
        state.content.trimEnd()
        result[state.name ?: state.fileName ?: ""] = when {
          state.fileName != null -> FileUpload(state.fileName!!, state.contentType, state.content.inputStream())
          state.isText -> state.content.toString(MimeTypes.textCharset)
          else -> state.content.toByteArray()
        }
        state = State()
      }
      else state.content.append(line)
    }
    return result
  }

  private fun InputStream.readLine(): TrimmableOutputStream? {
    val buf = TrimmableOutputStream(128)
    var b = read()
    while (b >= 0) {
      buf.write(b)
      if (b == '\n'.code) break
      b = read()
    }
    if (b == -1 && buf.size() == 0) return null
    return buf
  }

  private fun keyValue(s: String) = s.split('=', limit = 2).let { it[0].trim() to it.getOrNull(1)?.trim('"') }

  private class State() {
    var readingHeaders: Boolean = true
    var name: String? = null
    var fileName: String? = null
    var contentType: String? = null
    val content = TrimmableOutputStream(4096)
    val isText get() = contentType?.let { MimeTypes.isText(it) } ?: false
  }
}

private class TrimmableOutputStream(size: Int): ByteArrayOutputStream(size) {
  fun append(content: TrimmableOutputStream) = write(content.buf, 0, content.count)

  fun isEmpty() = count == 0

  fun startsWith(prefix: TrimmableOutputStream) =
    prefix.count <= count && Arrays.equals(buf, 0, prefix.count, prefix.buf, 0, prefix.count)

  fun trimEnd() = this.also {
    trimEnd(10); trimEnd(13)
  }

  fun trimEnd(byte: Byte) {
    if (count > 0 && buf[count - 1] == byte) count--
  }

  fun inputStream() = ByteArrayInputStream(buf, 0, count)
}

class FileUpload(val fileName: String, val contentType: String? = MimeTypes.typeFor(fileName), val stream: InputStream)
