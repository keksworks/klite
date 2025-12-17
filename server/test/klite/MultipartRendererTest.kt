package klite

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class MultipartRendererTest {
  @Test fun render() {
    val renderer = MultipartRenderer(boundary = "MyBoundary")
    expect(renderer.contentType).toEqual(MimeTypes.formData)
    expect(renderer.fullContentType).toEqual(MimeTypes.formData + "; boundary=MyBoundary")

    val output = ByteArrayOutputStream()
    renderer.render(output, mapOf(
      "name1" to "value1",
      "name2" to "Hello".toByteArray(),
      "my-file" to FileUpload("a.txt", stream = "Content of a.txt.".byteInputStream()),
      "null" to null
    ))
    expect(output.toString()).toEqual("""
      --MyBoundary
      Content-Disposition: form-data; name="name1"

      value1
      --MyBoundary
      Content-Disposition: form-data; name="name2"

      Hello
      --MyBoundary
      Content-Disposition: form-data; name="my-file"; filename="a.txt"
      Content-Type: text/plain; charset=UTF-8

      Content of a.txt.
      --MyBoundary--

    """.trimIndent().replace("\n", "\r\n"))
  }
}
