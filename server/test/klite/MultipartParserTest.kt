package klite

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test

class MultipartParserTest {
  val parser = MultipartParser()

  @Test fun parse() {
    val body = """
      -----------------------------9051914041544843365972754266
      Content-Disposition: form-data; name="text"
      Content-Type: text/plain

      text default
      -----------------------------9051914041544843365972754266
      Content-Disposition: form-data; name="file1"; filename="a.txt"
      Content-Type: text/plain

      Content of a.txt.
      Line2

      -----------------------------9051914041544843365972754266
      Content-Disposition: form-data; name="file2"; filename="a.html"
      Content-Type: text/html

      <!DOCTYPE html><title>Content of a.html.</title>

      -----------------------------9051914041544843365972754266--
    """.trimIndent()

    val result = parser.parse(body.byteInputStream())
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
    val boundary = "---XXX"
    val data = (-128..127).map { it.toByte() }.toByteArray()
    val body = (
      "\r\n$boundary\r\n"
      + "Content-Disposition: form-data; name=\"file\"; filename=\"a.bin\"\r\n"
      + "Content-Type: application/octet-stream\r\n\r\n"
    ).toByteArray() + data + "\r\n$boundary--\r\n".toByteArray()

    val result = parser.parse(body.inputStream())
    val file = result["file"] as FileUpload
    expect(file.fileName).toEqual("a.bin")
    expect(file.contentType).toEqual(MimeTypes.binary)
    val content = file.stream.readAllBytes()
    expect(content.size).toEqual(data.size)
    expect(content.contentEquals(data)).toEqual(true)
  }
}
