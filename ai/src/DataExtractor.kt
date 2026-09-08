package klite.ai

import klite.StatusCode.Companion.TooManyRequests
import klite.createFrom
import klite.http.HttpException
import klite.json.JsonMapper
import klite.json.parse
import klite.logger
import klite.nodes.Node
import klite.publicProperties
import klite.warn
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.createType

class DataExtractor(
  private val aiClient: AIClient,
  private val json: JsonMapper = JsonMapper()
) {
  private val log = logger()
  private val classPackageRegex = "\\b[\\w.]*\\.".toRegex()

  fun <T: Any> extract(text: String, type: KClass<T>, provided: Map<KProperty1<T, *>, Any?> = emptyMap(), extraPrompt: String = "", numAttempts: Int = 3): T {
    val props = type.publicProperties - provided.keys.mapTo(mutableSetOf()) { it.name } - "id"
    val keys = props.values.joinToString { "${it.name}: " + it.returnType.toString().replace(classPackageRegex, "") }
    var prompt = "Output plain json with keys $keys, ISO dates, numbers as strings with dots: $text\n$extraPrompt"
    var response: AIClient.Response? = null
    repeat(numAttempts) {
      try {
        response = aiClient.query(prompt, prevResponseId = response?.id)
        val jsonStr = response.text.stripMarkdown()
        if (provided.isEmpty()) return json.parse(jsonStr, type.createType())
        return type.createFrom(json.parse<Node>(jsonStr) + provided.mapKeys { it.key.name })
      } catch (e: Exception) {
        if ((e as? HttpException)?.statusCode == TooManyRequests || it == numAttempts - 1) throw e
        log.warn("Failed to extract ${type.simpleName}, retrying: $e")
        prompt = (if (response?.id != null) "" else prompt) + "\nTry again, got $e"
      }
    }
    error("Failed to extract ${type.simpleName} after $numAttempts attempts")
  }

  private fun String.stripMarkdown() = substringAfter("```json").substringBeforeLast("```")
}
