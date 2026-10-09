package klite.ai

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import klite.base64Encode
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URI

class OpenAIClientTest {
  @Test fun `remote document is referenced by url`() {
    val url = URI("https://files.pixit.vet/p1/visit/report.pdf")

    expect(url.toOpenAIContent()).toEqual(OpenAIClient.Content(type = "input_file", fileUrl = url, filename = "report.pdf"))
  }

  @Test fun `remote image is referenced by url`() {
    val url = URI("https://files.pixit.vet/p1/vaccination/scan.jpg")

    expect(url.toOpenAIContent()).toEqual(OpenAIClient.Content(type = "input_image", imageUrl = url))
  }

  @Test fun `local document is inlined as base64 data`() {
    val file = File.createTempFile("report", ".pdf").apply { writeBytes(byteArrayOf(1, 2, 3)) }

    val content = file.toURI().toOpenAIContent()

    expect(content).toEqual(OpenAIClient.Content(type = "input_file", fileData = URI("data:application/pdf;base64,${file.readBytes().base64Encode()}"), filename = file.name))
  }

  @Test fun `local image is inlined as base64 data`() {
    val file = File.createTempFile("scan", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }

    val content = file.toURI().toOpenAIContent()

    expect(content).toEqual(OpenAIClient.Content(type = "input_image", imageUrl = URI("data:image/jpeg;base64,${file.readBytes().base64Encode()}")))
  }

  @Test fun `data url document is inlined as is`() {
    val data = URI("data:application/pdf;base64,AAAA")

    expect(data.toOpenAIContent()).toEqual(OpenAIClient.Content(type = "input_file", fileData = data))
  }
}
