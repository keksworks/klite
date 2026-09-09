package klite.ai

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.io.InputStream
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.typeOf

class PDFExtractor(private val extractor: DataExtractor) {
  private val textStripper = PDFTextStripper()

  fun extractText(pdf: InputStream): String {
    val bytes = pdf.use { it.readBytes() }
    return Loader.loadPDF(bytes).use { textStripper.getText(it) }
  }

  inline fun <reified T: Any> extractData(pdf: InputStream, provided: Map<KProperty1<*, *>, Any?> = emptyMap(), extraPrompt: String = ""): T =
    extractData(pdf, typeOf<T>(), provided, extraPrompt)

  fun <T: Any> extractData(pdf: InputStream, type: KType, provided: Map<KProperty1<*, *>, Any?> = emptyMap(), extraPrompt: String = "", numAttempts: Int = 3): T {
    val text = extractText(pdf) + "\n" + extraPrompt
    return extractor.extract(text, type, null, provided, numAttempts)
  }
}
