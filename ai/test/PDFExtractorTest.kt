package klite.ai

import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.every
import io.mockk.mockk
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class PDFExtractorTest {
  val response = AIClient.Response("r1", "success", "test-model", """{"result": "test"}""")
  val aiClient = mockk<AIClient>(relaxed = true) {
    every { query(any()) } returns response
  }
  val extractor = PDFExtractor(aiClient)

  @Test fun `extract text from simple PDF`() {
    val pdfBytes = createPdf("Invoice 123\nAmount: 100.00 EUR\nDate: 2025-01-15")
    val text = extractor.extractText(ByteArrayInputStream(pdfBytes))

    expect(text).toContain("Invoice 123")
      .toContain("Amount: 100.00 EUR")
      .toContain("Date: 2025-01-15")
  }

  @Test fun `extract text from empty PDF`() {
    val pdfBytes = createPdf("")
    val text = extractor.extractText(ByteArrayInputStream(pdfBytes))

    expect(text.isBlank()).toEqual(true)
  }

  @Test fun `extract text from multi-page PDF`() {
    val doc = PDDocument()
    doc.addPage(PDPage(PDRectangle.A4))
    doc.addPage(PDPage(PDRectangle.A4))

    PDPageContentStream(doc, doc.getPage(0)).use {
      it.beginText()
      it.setFont(PDType1Font(FontName.HELVETICA), 12f)
      it.newLineAtOffset(50f, 700f)
      it.showText("Page 1 content")
      it.endText()
    }

    PDPageContentStream(doc, doc.getPage(1)).use {
      it.beginText()
      it.setFont(PDType1Font(FontName.HELVETICA), 12f)
      it.newLineAtOffset(50f, 700f)
      it.showText("Page 2 content")
      it.endText()
    }

    val out = ByteArrayOutputStream()
    doc.save(out)
    doc.close()

    val text = extractor.extractText(ByteArrayInputStream(out.toByteArray()))

    expect(text).toContain("Page 1 content")
    expect(text).toContain("Page 2 content")
  }

  @Test fun `stripMarkdown removes code fences`() {
    every { aiClient.query(any()) } returns response.copy(text = "```json\n{\"key\": \"value\"}\n```")
    val pdfBytes = createPdf("test")
    val data = extractor.extractData<TestClass>(ByteArrayInputStream(pdfBytes))
    expect(data.key).toEqual("value")
  }

  @Test fun `extractData retries on failure`() {
    var attempts = 0
    every { aiClient.query(any(), any(), any(), any()) } answers {
      attempts++
      if (attempts < 3) throw RuntimeException("API error")
      response.copy(text = """{"key": "recovered"}""")
    }
    val pdfBytes = createPdf("test")
    val data = extractor.extractData<TestClass>(ByteArrayInputStream(pdfBytes))
    expect(data.key).toEqual("recovered")
    expect(attempts).toEqual(3)
  }

  private fun createPdf(text: String): ByteArray {
    val doc = PDDocument()
    doc.addPage(PDPage(PDRectangle.A4))
    PDPageContentStream(doc, doc.getPage(0)).use {
      it.beginText()
      it.setFont(PDType1Font(FontName.HELVETICA), 12f)
      it.newLineAtOffset(50f, 700f)
      for (line in text.split("\n")) {
        it.showText(line)
        it.newLineAtOffset(0f, -20f)
      }
      it.endText()
    }
    val out = ByteArrayOutputStream()
    doc.save(out)
    doc.close()
    return out.toByteArray()
  }

  data class TestClass(val key: String)
}
