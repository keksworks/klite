package klite.ai

import klite.http.HttpException
import klite.json.JsonMapper
import klite.json.toJsonSchema
import klite.logger
import klite.nodes.Node
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

  inline fun <reified T: Any> extract(text: String = "", vararg fileUrl: URI, provided: Map<KProperty1<*, *>, Any?> = emptyMap(), params: Node = emptyMap(), numAttempts: Int = 3): T =
    extract(text, typeOf<T>(), *fileUrl, provided = provided, params = params, numAttempts = numAttempts)

  fun <T: Any> extract(text: String, type: KType, vararg fileUrl: URI, provided: Map<KProperty1<*, *>, Any?> = emptyMap(), params: Node = emptyMap(), numAttempts: Int = 3): T =
    extractResponse<T>(text, type, *fileUrl, provided = provided, params = params, numAttempts = numAttempts).first

  inline fun <reified T: Any> extractResponse(text: String = "", vararg fileUrl: URI, provided: Map<KProperty1<*, *>, Any?> = emptyMap(), params: Node = emptyMap(), numAttempts: Int = 3): Pair<T, AIClient.Response> =
    extractResponse(text, typeOf<T>(), *fileUrl, provided = provided, params = params, numAttempts = numAttempts)

  fun <T: Any> extractResponse(text: String, type: KType, vararg fileUrl: URI, provided: Map<KProperty1<*, *>, Any?> = emptyMap(), params: Node = emptyMap(), numAttempts: Int = 3): Pair<T, AIClient.Response> {
    val providedText = if (provided.isNotEmpty()) ", use these provided values: " + provided.entries.joinToString { "${it.key.name}=${it.value}" } else ""
    val schema = type.toJsonSchema()?.withoutDefaultFields()
    val typedClient = aiClient as? TypedExtractionClient
    var prompt = "Extract $type data as plain json, schema $schema, skip non-required fields if not available:\n$text\n$providedText"
    var state = text + providedText
    var files: Array<out URI> = fileUrl
    var response: AIClient.Response? = null
    repeat(numAttempts) {
      try {
        response = if (typedClient != null && schema != null) typedClient.extractTyped(state, schema, *files, params = params)
          else aiClient.query(prompt, *files, prevResponseId = response?.id, params = params)
        val jsonStr = response.text.stripMarkdown()
        return json.parse<T>(jsonStr, type) to response
      } catch (e: Exception) {
        if ((e as? HttpException)?.statusCode?.isClientError == true || it == numAttempts - 1) throw e
        log.warn("Failed to extract $type, retrying: $e")
        val continued = response?.id != null
        prompt = (if (continued) "" else prompt) + "\nTry again, got $e"
        state += "\nTry again, got $e"
        if (continued) files = emptyArray()
      }
    }
    error("Failed to extract $type after $numAttempts attempts")
  }

  private fun String.stripMarkdown() = substringAfter("```json").substringBeforeLast("```")
}

private val skippedDefaultFields = setOf("id", "createdAt", "updatedAt")

/** Removes system fields that have default values, as they are normally generated locally rather than extracted */
@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.withoutDefaultFields(): Map<String, Any?> {
  val properties = this["properties"] as? Map<*, *>
  val result = if (properties == null) this else {
    val required = (this["required"] as? Collection<*>)?.mapTo(HashSet()) { it.toString() }.orEmpty()
    val fields = properties.entries.mapNotNull { (name, value) ->
      val fieldName = name.toString()
      if (fieldName in skippedDefaultFields && fieldName !in required) null
      else fieldName to value.withoutDefaultFields()
    }.toMap()
    this + ("properties" to fields)
  }
  val items = result["items"] as? Map<String, Any?>
  return if (items == null) result else result + ("items" to items.withoutDefaultFields())
}

@Suppress("UNCHECKED_CAST")
private fun Any?.withoutDefaultFields(): Any? = if (this is Map<*, *>) (this as Map<String, Any?>).withoutDefaultFields() else this
