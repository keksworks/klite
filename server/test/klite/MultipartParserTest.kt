package klite

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.reflect.typeOf

class MultipartParserTest {
  val parser = MultipartParser()

  @Test fun parse() {
    val boundary = "----9051914041544843365972754266"
    val body = """
      --$boundary
      Content-Disposition: form-data; name="text"
      Content-Type: text/plain

      text default
      --$boundary
      Content-Disposition: form-data; name="file1"; filename="a.txt"
      Content-Type: text/plain

      Content of a.txt.
      Line2

      --$boundary
      Content-Disposition: form-data; name="file2"; filename="a.html"
      Content-Type: text/html

      <!DOCTYPE html><title>Content of a.html.</title>

      --$boundary--
    """.trimIndent()

    val result = parser.parse(body.byteInputStream(), "${MimeTypes.formData}; boundary=$boundary")
    expect(result["text"]).toEqual("text default")

    val file1 = result["file1"] as FileUpload
    expect(file1.fileName).toEqual("a.txt")
    expect(file1.contentType).toEqual(MimeTypes.text)
    expect(file1.stream.reader().readText()).toEqual("Content of a.txt.\nLine2\n")

    val file2 = result["file2"] as FileUpload
    expect(file2.fileName).toEqual("a.html")
    expect(file2.contentType).toEqual(MimeTypes.html)
    expect(file2.stream.reader().readText()).toEqual("<!DOCTYPE html><title>Content of a.html.</title>\n")
  }

  @Test fun `parse binary`() {
    val boundary = "XXX"
    val data = (-128..127).map { it.toByte() }.toByteArray()
    val body = (
      "\r\n--$boundary\r\n"
      + "Content-Disposition: form-data; name=\"file\"; filename=\"a.bin\"\r\n"
      + "Content-Type: application/octet-stream\r\n\r\n"
    ).toByteArray() + data + "\r\n--$boundary--\r\n".toByteArray()

    val result = parser.parse(body.inputStream(), "${MimeTypes.formData}; boundary=$boundary")
    val file = result["file"] as FileUpload
    expect(file.fileName).toEqual("a.bin")
    expect(file.contentType).toEqual(MimeTypes.binary)
    val content = file.stream.readAllBytes()
    expect(content.size).toEqual(data.size)
    expect(content.contentEquals(data)).toEqual(true)
  }

  @Test fun `declared boundary is authoritative`() {
    val body = "--evil\r\nContent-Type: text/plain\r\n\r\nx\r\n--evil--\r\n"
    expect(parser.parse(body.byteInputStream(), "${MimeTypes.formData}; boundary=evil")[""]).toEqual("x")
    expect { parser.parse(body.byteInputStream(), "${MimeTypes.formData}; boundary=real") }.toThrow<BadRequestException>()
    expect { parser.parse(body.byteInputStream(), MimeTypes.formData) }.toThrow<BadRequestException>()
    expect { parser.parse(body.byteInputStream(), "${MimeTypes.formData}; boundary=") }.toThrow<BadRequestException>()
  }

  @Test fun `falls back to the body boundary when no content type is given`() {
    val body = "--evil\r\nContent-Type: text/plain\r\n\r\nx\r\n--evil--\r\n"
    expect(parser.parse(body.byteInputStream())[""]).toEqual("x")
    expect(parser.parse(body.byteInputStream(), null)[""]).toEqual("x")
    expect(parser.parse<Map<String, Any>>(body.byteInputStream(), typeOf<Map<String, Any>>())[""]).toEqual("x")
  }

  @Test fun `empty body`() {
    expect { parser.parse(ByteArray(0).inputStream(), "${MimeTypes.formData}; boundary=B") }.toThrow<BadRequestException>()
    expect { parser.parse(ByteArray(0).inputStream()) }.toThrow<BadRequestException>()
  }

  @Test fun `missing boundary in body`() {
    expect { parser.parse("hello\r\nworld\r\n".byteInputStream(), "${MimeTypes.formData}; boundary=B") }
      .toThrow<BadRequestException>()
  }

  @Test fun `malformed header line is ignored`() {
    val body = "--B\r\ngarbage\r\nContent-Type: text/plain\r\n\r\nx\r\n--B--\r\n"
    expect(parser.parse(body.byteInputStream(), "${MimeTypes.formData}; boundary=B")[""]).toEqual("x")
  }

  @Test fun `quoted values may contain semicolons`() {
    val body = "--B\r\nContent-Disposition: form-data; name=\"a;b\"; filename=\"c;d.txt\"\r\nContent-Type: text/plain\r\n\r\nx\r\n--B--\r\n"
    val file = parser.parse(body.byteInputStream(), "${MimeTypes.formData}; boundary=B")["a;b"] as FileUpload
    expect(file.fileName).toEqual("c;d.txt")
  }

  @Test fun `semicolons inside a quoted value can't inject parameters`() {
    val body = "--B\r\nContent-Disposition: form-data; name=\"f\"; filename=\"a\\\"; name=admin\"\r\nContent-Type: text/plain\r\n\r\nx\r\n--B--\r\n"
    val result = parser.parse(body.byteInputStream(), "${MimeTypes.formData}; boundary=B")
    expect(result.keys).toEqual(setOf("f"))
  }

  @Test fun `filename is sanitized`() {
    expect(fileName("C:\\dir\\a.txt")).toEqual("a.txt")
    expect(fileName("a.txt")).toEqual("a.txt")
    expect(fileName("../../etc/passwd")).toEqual("passwd")
    expect(fileName("/etc/passwd")).toEqual("passwd")
    expect(fileName("..\\..\\x.txt")).toEqual("x.txt")
    expect(fileName("C:\\Users\\me\\x.txt")).toEqual("x.txt")
    expect(fileName("..")).toEqual("")
    expect(fileName("a\u0000b.txt")).toEqual("ab.txt")
    expect(fileName("hellö.txt")).toEqual("hellö.txt")
  }

  @Test fun `parse from exchange`() {
    val body = "--B\r\nContent-Type: text/plain\r\n\r\nx\r\n--B--\r\n"
    expect(parseExchange(body, "${MimeTypes.formData}; boundary=B")[""]).toEqual("x")
    expect { parseExchange(body, "${MimeTypes.formData}; boundary=other") }.toThrow<BadRequestException>()
  }

  private fun parseExchange(body: String, contentType: String): Map<String, Any> {
    val exchange = mockk<HttpExchange>(relaxed = true)
    every { exchange.requestStream } returns body.byteInputStream()
    every { exchange.requestType } returns contentType
    return parser.parse(exchange, typeOf<Map<String, Any>>())
  }

  private fun fileName(fileName: String): String {
    val body = "--B\r\nContent-Disposition: form-data; name=\"f\"; filename=\"$fileName\"\r\n\r\na\r\n--B--\r\n"
    return (parser.parse(body.byteInputStream(), "${MimeTypes.formData}; boundary=B")["f"] as FileUpload).fileName
  }
}
