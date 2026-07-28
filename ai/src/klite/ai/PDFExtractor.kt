package klite.ai

import klite.createFrom
import klite.json.JsonMapper
import klite.json.parse
import klite.logger
import klite.nodes.Node
import klite.publicProperties
import klite.warn
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.io.InputStream
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.createType

class PDFExtractor(private val aiClient: AIClient, private val json: JsonMapper = JsonMapper()) {
  private val textStripper = PDFTextStripper()
  private val log = logger()

  fun extractText(pdf: InputStream): String {
    val bytes = pdf.use { it.readBytes() }
    return Loader.loadPDF(bytes).use { textStripper.getText(it) }
  }

  inline fun <reified T: Any> extractData(pdf: InputStream, provided: Map<KProperty1<T, *>, Any?> = emptyMap(), extraPrompt: String = ""): T =
    extractData(pdf, T::class, provided, extraPrompt)

  private val classPackageRegex = "\\b[\\w.]*\\.".toRegex()

  fun <T: Any> extractData(pdf: InputStream, type: KClass<T>, provided: Map<KProperty1<T, *>, Any?> = emptyMap(), extraPrompt: String = "", numAttempts: Int = 3): T {
    val text = extractText(pdf)
    val props = type.publicProperties - provided.keys.mapTo(mutableSetOf()) { it.name } - "id"
    val keys = props.values.joinToString { "${it.name}: " + it.returnType.toString().replace(classPackageRegex, "") }
    var prompt = "Output plain json with keys $keys, ISO dates, numbers as strings with dots: $text\n$extraPrompt"
    repeat(numAttempts) {
      try {
        val jsonStr = aiClient.query(prompt).stripMarkdown()
        if (provided.isEmpty()) return json.parse(jsonStr, type.createType())
        return type.createFrom(json.parse<Node>(jsonStr) + provided.mapKeys { it.key.name })
      } catch (e: Exception) {
        if (e.message?.startsWith("429:") == true || it == numAttempts - 1) throw e
        log.warn("Failed to extract ${type.simpleName}, retrying: $e")
        prompt += "\nPrevious error: ${e.message}"
      }
    }
    error("Failed to extract ${type.simpleName} after $numAttempts attempts")
  }

  private fun String.stripMarkdown() = substringAfter("```json").substringBefore("```")
}
