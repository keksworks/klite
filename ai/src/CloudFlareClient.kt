package klite.ai

import klite.Config
import klite.ai.AIClient.TokenUsage
import klite.http.timeout
import klite.json.JsonHttpClient
import klite.json.JsonMapper
import klite.json.JsonProperty
import klite.nodes.Node
import java.net.URI
import java.net.http.HttpClient

// https://developers.cloudflare.com/workers-ai/models/clairic/
/**
 * Client for Cloudflare Workers AI extraction models (Clairic).
 *
 * Clairic does not generate text: it reads a state (text plus up to four images or PDFs)
 * and returns one typed value per requested field, so it is meant to be used for data extraction,
 * e.g. via [DataExtractor], rather than as a chat client.
 */
open class CloudFlareClient(
  httpClient: HttpClient,
  accountId: String = Config["CLOUDFLARE_ACCOUNT_ID"],
  apiKey: String = Config["CLOUDFLARE_API_TOKEN"],
  val model: String = Config.optional("CLOUDFLARE_MODEL", "@cf/cloudflare/clairic"),
  val params: Node = emptyMap()
): AIClient, TypedExtractionClient {
  private val http = JsonHttpClient(Config.optional("CLOUDFLARE_URL", "https://api.cloudflare.com/client/v4/accounts/$accountId/ai/run"),
    http = httpClient, json = JsonMapper(), maxLoggedLen = maxLoggedLen, logStart = true,
    reqModifier = { header("Authorization", "Bearer $apiKey").timeout(timeout) })
  private val path = if (model.startsWith("@")) "/$model" else "/@cf/cloudflare/$model"

  override fun query(input: String, vararg fileUrl: URI, prevResponseId: String?, params: Node): AIClient.Response {
    val fields = params["fields"] as? Node ?: throw IllegalArgumentException("Clairic requires typed 'fields' in params, use extractTyped() for a Kotlin type")
    return respond(extract(input, fields, *fileUrl, params = params - "fields"))
  }

  override fun stream(input: String, vararg fileUrl: URI, params: Node): Sequence<String> =
    throw UnsupportedOperationException("Clairic does not generate text, use extractTyped() instead")

  override fun extractTyped(state: String, schema: Map<String, Any?>, vararg fileUrl: URI, params: Node): AIClient.Response {
    val fields = schema.toClairicFields() ?: error("Clairic cannot extract fields from schema $schema")
    return respond(extract(state, fields, *fileUrl, params = params), schema)
  }

  fun extract(state: String, fields: Node, vararg fileUrl: URI, params: Node = emptyMap()): Response {
    val images = fileUrl.filter { it.isImage }
    val documents = fileUrl.filterNot { it.isImage }
    val body = mapOf("model" to model, "fields" to fields) + this.params + params + mapOf(
      "state" to state,
      "images" to images.map { it.toClairicMedia() }.takeIf { it.isNotEmpty() },
      "documents" to documents.map { it.toClairicMedia() }.takeIf { it.isNotEmpty() }
    )
    return http.post<Envelope>(path, body).result
  }

  private fun respond(response: Response, schema: Map<String, Any?>? = null) =
    response.toGenericResponse(schema).also { it.usage?.let(TokenMetrics::record) }

  data class Envelope(val result: Response)
  data class Answer(val value: Any? = null)
  data class Usage(@JsonProperty("input_tokens") val inputTokens: Long, @JsonProperty("output_tokens") val outputTokens: Long) {
    fun toTokenUsage(model: String) = TokenUsage(model, inputTokens, outputTokens)
  }

  data class Response(val model: String, val answers: Map<String, Answer>, val truncated: Boolean = false,
                      val usage: Usage) {
    fun toGenericResponse(schema: Map<String, Any?>? = null) = AIClient.Response(null, if (truncated) "truncated" else "success", model,
      clairicJson.render(answers.mapNotNull { (name, answer) -> answer.value?.let { name to it } }.toMap().normalizeLists(schema)),
      usage.toTokenUsage(model))
  }
}

internal fun URI.toClairicMedia(): Node = mapOf(
  "content_type" to (mimeType ?: "application/octet-stream"),
  "base64" to inlineBase64()
)

private val clairicJson = JsonMapper()
private val notNullableTypes = setOf("text", "number", "boolean", "enum", "object")
private val rowFieldTypes = notNullableTypes - "object"

/** Converts a JSON schema (e.g. from [klite.json.toJsonSchema]) into Clairic typed field specs */
internal fun Map<String, Any?>.toClairicFields(): Node? {
  val properties = this["properties"] as? Map<*, *> ?: return null
  val required = (this["required"] as? Collection<*>)?.mapTo(HashSet()) { it.toString() }.orEmpty()
  return properties.entries.mapNotNull { (name, value) ->
    (value as? Map<*, *>)?.toClairicField(name.toString() in required)?.let { name.toString() to it }
  }.toMap().takeIf { it.isNotEmpty() }
}

private fun Map<*, *>.toClairicField(required: Boolean): Node? {
  val field = when (this["type"]) {
    "string" -> when (this["format"]) {
      "date", "time", "date-time", "duration" -> mapOf("type" to (this["format"] as String).replace("date-time", "datetime"))
      else -> (this["enum"] as? Collection<*>)?.let { values ->
        mapOf("type" to "enum", "enum" to values.map { (it as? Enum<*>)?.name ?: it.toString() })
      } ?: mapOf("type" to "text")
    }
    "boolean" -> mapOf("type" to "boolean")
    "integer" -> mapOf("type" to "number", "integer" to true)
    "number" -> mapOf("type" to "number")
    "array" -> (this["items"] as? Map<*, *>)?.toClairicListItems()?.let { mapOf("type" to "list") + it }
    "object" -> toClairicObjectProperties()?.let { mapOf("type" to "object", "properties" to it) }
    else -> null
  }
  return field?.let { if (required && it["type"] in notNullableTypes) it + ("nullable" to false) else it }
}

private fun Map<*, *>.toClairicListItems(): Node? = when (this["type"]) {
  "string" -> mapOf("items" to if (this["format"] == "date") "date" else "text")
  "integer", "number" -> mapOf("items" to "number")
  "object" -> toClairicObjectProperties()?.let { mapOf("items" to mapOf("type" to "object", "properties" to it)) }
  else -> null
}

/** Clairic rows support only scalar values, so nested lists are extracted as text and restored by [normalizeLists] */
private fun Map<*, *>.toClairicObjectProperties(): Node? {
  val properties = this["properties"] as? Map<*, *> ?: return null
  val fields = properties.entries.mapNotNull { (name, value) ->
    val schema = value as? Map<*, *> ?: return@mapNotNull null
    val field = if (schema["type"] == "array") mapOf("type" to "text")
      else schema.toClairicField(false)?.takeIf { it["type"] in rowFieldTypes }
    field?.let { name.toString() to it }
  }
  return fields.takeIf { it.size == properties.size }?.toMap()?.takeIf { it.isNotEmpty() }
}

/** Restores list properties of object rows, filling absent ones with empty lists */
internal fun Map<String, Any?>.normalizeLists(schema: Map<String, Any?>?): Map<String, Any?> {
  val properties = schema?.get("properties") as? Map<*, *> ?: return this
  return mapValues { (name, value) ->
    val items = (properties[name] as? Map<*, *>)?.takeIf { it["type"] == "array" }?.get("items") as? Map<*, *>
    if (value !is Collection<*> || items?.get("type") != "object") value
    else value.map { row -> (row as? Map<*, *>)?.normalizeRow(items) ?: row }
  }
}

private fun Map<*, *>.normalizeRow(schema: Map<*, *>): Map<String, Any?> {
  val properties = schema["properties"] as? Map<*, *> ?: return this as Map<String, Any?>
  val row = entries.associate { (name, value) ->
    name.toString() to if ((properties[name] as? Map<*, *>)?.get("type") == "array") value.toListOrEmpty() else value
  }.toMutableMap()
  properties.forEach { (name, property) ->
    if ((property as? Map<*, *>)?.get("type") == "array") row.putIfAbsent(name.toString(), emptyList<Any?>())
  }
  return row
}

private fun Any?.toListOrEmpty(): Any? = when (this) {
  is String -> split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }
  is Collection<*> -> this
  else -> emptyList()
}
