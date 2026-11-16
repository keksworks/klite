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
  /** A request always has a Content-Type here (it selects this parser), so its boundary is authoritative, never the body's */
  @Suppress("UNCHECKED_CAST")
  override fun <T> parse(e: HttpExchange, type: KType): T = e.requestStream.use { parse(it, e.requestType) } as T

  @Suppress("UNCHECKED_CAST")
  override fun <T: Any> parse(input: InputStream, type: KType) = parse(input) as T

  fun parse(input: InputStream): Map<String, Any> = parse(input, null)

  /** [requestContentType] is absent when there's no request to take the boundary from, then it's read from the body */
  internal fun parse(input: InputStream, requestContentType: String?): Map<String, Any> {
    val declaredBoundary = requestContentType?.let { it.multipartBoundary() ?: throw BadRequestException("Invalid or missing multipart boundary") }
    val reader = LineReader(input)
    val delimiter = readDelimiter(reader, declaredBoundary)

    val result = mutableMapOf<String, Any>()
    var state = State()
    while (true) {
      val line = if (state.readingHeaders) TrimmableOutputStream(128) else state.content
      val offset = state.content.size() // content lines are read in place, only a delimiter line is rolled back to this offset
      if (!reader.readLine(line)) break
      if (state.readingHeaders) {
        line.trimEnd()
        if (line.isEmpty()) { state.readingHeaders = false; continue }
        val headerLine = line.toString(MimeTypes.textCharset)
        val (header, value) = headerLine.substringBefore(':').trim() to headerLine.substringAfter(':', "").trim()
        if (header.equals("content-type", ignoreCase = true)) {
          state.contentType = value
        } else if (header.equals("content-disposition", ignoreCase = true)) {
          val params = value.headerParams()
          state.name = params["name"]
          state.fileName = params["filename"]?.safeFileName()
        } else if (header.equals(nameHeader, ignoreCase = true)) {
          state.name = value
        }
      } else if (line.startsWith(delimiter, offset)) {
        state.content.truncateTo(offset)
        state.content.trimEnd()
        result[state.name ?: state.fileName ?: ""] = when {
          state.fileName != null -> FileUpload(state.fileName!!, state.contentType, state.content.inputStream())
          state.isText -> state.content.toString(MimeTypes.textCharset)
          else -> state.content.toByteArray()
        }
        state = State()
      }
    }
    return result
  }

  /** A declared boundary is authoritative and may be preceded by a preamble; without one the first non-empty body line is used (backwards compatible) */
  private fun readDelimiter(reader: LineReader, declaredBoundary: String?): TrimmableOutputStream {
    val declared = declaredBoundary?.let { "--$it".toDelimiter() }
    while (true) {
      val line = TrimmableOutputStream(128)
      if (!reader.readLine(line)) throw BadRequestException("Multipart boundary not found")
      line.trimEnd()
      if (declared?.let { line.startsWith(it) } ?: !line.isEmpty()) return declared ?: line
    }
  }

  private fun String.toDelimiter() = TrimmableOutputStream(length).also { it.write(toByteArray()) }

  private class State {
    var readingHeaders: Boolean = true
    var name: String? = null
    var fileName: String? = null
    var contentType: String? = null
    val content = TrimmableOutputStream(4096)
    val isText get() = contentType?.let { MimeTypes.isText(it) } ?: (fileName == null)
  }
}

/** Extracts the `boundary` parameter of a multipart Content-Type header */
private fun String.multipartBoundary() = headerParams()["boundary"]?.takeIf { it.isNotEmpty() }

/** Keeps only the base name without unsafe characters, so a crafted `filename` can't traverse paths */
private fun String.safeFileName() = replace('\\', '/').substringAfterLast('/')
  .filter { it >= ' ' && it != '\u007f' }.takeIf { it != "." && it != ".." }.orEmpty()

private class LineReader(input: InputStream) {
  private val stream = input.buffered()
  private val buf = ByteArray(DEFAULT_BUFFER_SIZE)
  private var pos = 0
  private var limit = 0

  /** Reads up to and including the newline into [target], false when the stream ends before any byte is read */
  fun readLine(target: TrimmableOutputStream): Boolean {
    var read = false
    while (true) {
      if (pos >= limit) { // >= because the end of stream is recorded as a negative limit
        limit = stream.read(buf)
        pos = 0
        if (limit < 1) return read
      }
      val start = pos
      while (pos < limit && buf[pos] != '\n'.code.toByte()) pos++
      val found = pos < limit
      if (found) pos++ // the line includes its newline
      if (pos > start) {
        target.write(buf, start, pos - start)
        read = true
      }
      if (found) return true
    }
  }
}

private class TrimmableOutputStream(size: Int): ByteArrayOutputStream(size) {
  fun isEmpty() = count == 0

  fun startsWith(prefix: TrimmableOutputStream, offset: Int = 0) =
    offset + prefix.count <= count && Arrays.equals(buf, offset, offset + prefix.count, prefix.buf, 0, prefix.count)

  fun truncateTo(size: Int) {
    count = size
  }

  fun trimEnd() = this.also {
    trimEnd(10); trimEnd(13)
  }

  fun trimEnd(byte: Byte) {
    if (count > 0 && buf[count - 1] == byte) count--
  }

  fun inputStream() = ByteArrayInputStream(buf, 0, count)
}

class FileUpload(val fileName: String, val contentType: String? = MimeTypes.typeFor(fileName), val stream: InputStream)
