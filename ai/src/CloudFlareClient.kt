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
    return extract(input, fields, *fileUrl, params = params - "fields").toGenericResponse().also { it.usage?.let(TokenMetrics::record) }
  }

  override fun stream(input: String, vararg fileUrl: URI, params: Node): Sequence<String> =
    throw UnsupportedOperationException("Clairic does not generate text, use extractTyped() instead")

  override fun extractTyped(state: String, schema: Map<String, Any?>, vararg fileUrl: URI, params: Node): AIClient.Response {
    val fields = schema.toClairicFields() ?: error("Clairic cannot extract fields from schema $schema")
    return extract(state, fields, *fileUrl, params = params).toGenericResponse(schema).also { it.usage?.let(TokenMetrics::record) }
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

  data class Envelope(val result: Response)
  data class Media(@JsonProperty("content_type") val contentType: String, val base64: String)
  data class Answer(val type: String, val value: Any? = null, val guess: Any? = null, val confidence: Double? = null,
                    @JsonProperty("p_null") val pNull: Double? = null, val span: List<Int>? = null)
  data class Usage(@JsonProperty("input_tokens") val inputTokens: Long, @JsonProperty("output_tokens") val outputTokens: Long,
                   val passes: Int? = null) {
    fun toTokenUsage(model: String) = TokenUsage(model, inputTokens, outputTokens)
  }

  data class Response(val model: String, val answers: Map<String, Answer>, val truncated: Boolean = false,
                      val usage: Usage) {
    fun toGenericResponse(schema: Map<String, Any?>? = null) = AIClient.Response(null, if (truncated) "truncated" else "success", model,
      JsonMapper().render(answers.mapNotNull { (name, answer) -> answer.value?.let { name to it } }.toMap().normalizeLists(schema)),
      usage.toTokenUsage(model))
  }
}

internal fun URI.toClairicMedia() = CloudFlareClient.Media(
  mimeType ?: "application/octet-stream",
  inlineBase64()
)

/** Converts a JSON schema (e.g. from [klite.json.toJsonSchema]) into Clairic typed field specs */
internal fun Map<String, Any?>.toClairicFields(): Node? {
  val properties = this["properties"] as? Map<*, *> ?: return null
  val required = (this["required"] as? Collection<*>)?.mapTo(HashSet()) { it.toString() }.orEmpty()
  return properties.entries.mapNotNull { (name, value) ->
    (value as? Map<*, *>)?.toClairicField(name.toString() in required)?.let { name.toString() to it }
  }.toMap().takeIf { it.isNotEmpty() }
}

private val notNullableTypes = setOf("text", "number", "boolean", "enum", "object")

private fun Map<*, *>.toClairicField(required: Boolean): Node? {
  val field = when (this["type"]) {
    "string" -> toClairicStringField()
    "boolean" -> mapOf("type" to "boolean")
    "integer" -> mapOf("type" to "number", "integer" to true)
    "number" -> mapOf("type" to "number")
    "array" -> (this["items"] as? Map<*, *>)?.toClairicListItems()?.let { mapOf("type" to "list") + it }
    "object" -> toClairicObjectProperties()?.let { mapOf("type" to "object", "properties" to it) }
    else -> null
  }
  return field?.let { if (required && it["type"] in notNullableTypes) it + ("nullable" to false) else it }
}

private fun Map<*, *>.toClairicStringField(): Node = when (this["format"]) {
  "date", "time", "date-time", "duration" -> mapOf("type" to (this["format"] as String).replace("date-time", "datetime"))
  else -> (this["enum"] as? Collection<*>)?.let { values ->
    mapOf("type" to "enum", "enum" to values.map { (it as? Enum<*>)?.name ?: it.toString() })
  } ?: mapOf("type" to "text")
}

private fun Map<*, *>.toClairicListItems(): Node? = when (this["type"]) {
  "string" -> mapOf("items" to if (this["format"] == "date") "date" else "text")
  "integer", "number" -> mapOf("items" to "number")
  "object" -> toClairicObjectProperties()?.let { mapOf("items" to mapOf("type" to "object", "properties" to it)) }
  else -> null
}

/** Clairic object properties support only scalar values: text, number, boolean and enum */
private fun Map<*, *>.toClairicObjectProperties(): Node? {
  val properties = this["properties"] as? Map<*, *> ?: return null
  val fields = properties.entries.mapNotNull { (name, value) ->
    (value as? Map<*, *>)?.toClairicObjectProperty()?.let { name.toString() to it }
  }
  return fields.takeIf { it.size == properties.size }?.toMap()?.takeIf { it.isNotEmpty() }
}

/** Row properties cannot be lists, so nested lists are extracted as text and split back in [normalizeLists] */
private fun Map<*, *>.toClairicObjectProperty(): Node? = when (this["type"]) {
  "array" -> mapOf("type" to "text")
  else -> toClairicScalarField()
}

private fun Map<*, *>.toClairicScalarField(): Node? = when (this["type"]) {
  "string" -> toClairicStringField().takeIf { it["type"] == "text" || it["type"] == "enum" }
  "boolean" -> mapOf("type" to "boolean")
  "integer" -> mapOf("type" to "number", "integer" to true)
  "number" -> mapOf("type" to "number")
  else -> null
}

/** Splits list properties of object rows back into lists, filling absent ones with empty lists */
@Suppress("UNCHECKED_CAST")
internal fun Map<String, Any?>.normalizeLists(schema: Map<String, Any?>?): Map<String, Any?> {
  val properties = schema?.get("properties") as? Map<*, *> ?: return this
  return mapValues { (name, value) ->
    val property = properties[name] as? Map<*, *>
    if (property?.get("type") == "array") value.normalizeList(property["items"] as? Map<*, *>) else value
  }
}

private fun Any?.normalizeList(items: Map<*, *>?): Any? =
  if (this is Collection<*> && items?.get("type") == "object") map { (it as? Map<*, *>)?.normalizeRow(items) ?: it }
  else this

@Suppress("UNCHECKED_CAST")
private fun Map<*, *>.normalizeRow(schema: Map<*, *>): Map<String, Any?> {
  val properties = schema["properties"] as? Map<*, *> ?: return this as Map<String, Any?>
  val row = entries.associate { (name, value) ->
    val property = properties[name] as? Map<*, *>
    name.toString() to if (property?.get("type") == "array") value.toListOrEmpty() else value
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
