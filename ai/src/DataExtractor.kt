package klite.ai

import klite.StatusCode.Companion.TooManyRequests
import klite.http.HttpException
import klite.json.JsonMapper
import klite.json.toJsonSchema
import klite.logger
import klite.warn
import java.net.URI
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.typeOf

class DataExtractor(
  private val aiClient: AIClient,
  private val json: JsonMapper = JsonMapper()
) {
  private val log = logger()

  inline fun <reified T: Any> extract(text: String = "", imageUrl: URI? = null, provided: Map<KProperty1<*, *>, Any?> = emptyMap()): T =
    extract(text, typeOf<T>(), imageUrl, provided)

  fun <T: Any> extract(text: String, type: KType, imageUrl: URI? = null, provided: Map<KProperty1<*, *>, Any?> = emptyMap(), numAttempts: Int = 3): T {
    val providedText = if (provided.isNotEmpty()) ", use these provided values: " + provided.entries.joinToString { "${it.key.name}=${it.value}" } else ""
    var prompt = "Output plain json of $type according to schema ${type.toJsonSchema()}, skip 'id' and non-required fields if not available:\n$text\n$providedText"
    var response: AIClient.Response? = null
    repeat(numAttempts) {
      try {
        response = aiClient.query(prompt, imageUrl = imageUrl, prevResponseId = response?.id)
        val jsonStr = response.text.stripMarkdown()
        if (provided.isEmpty()) return json.parse(jsonStr, type)
        return json.parse(jsonStr, type)
      } catch (e: Exception) {
        if ((e as? HttpException)?.statusCode == TooManyRequests || it == numAttempts - 1) throw e
        log.warn("Failed to extract $type, retrying: $e")
        prompt = (if (response?.id != null) "" else prompt) + "\nTry again, got $e"
      }
    }
    error("Failed to extract $type after $numAttempts attempts")
  }

  private fun String.stripMarkdown() = substringAfter("```json").substringBeforeLast("```")
}
