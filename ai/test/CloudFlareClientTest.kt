package klite.ai

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import klite.Decimal
import klite.json.JsonMapper
import klite.json.parse
import klite.json.toJsonSchema
import klite.nodes.Node
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import kotlin.reflect.typeOf

class CloudFlareClientTest {
  @Test fun `json schema is converted to clairic fields`() {
    expect(typeOf<Invoice>().toJsonSchema()!!.toClairicFields()).toEqual(mapOf(
      "number" to mapOf("type" to "text", "nullable" to false),
      "seller" to mapOf("type" to "text"),
      "date" to mapOf("type" to "date"),
      "amount" to mapOf("type" to "number", "nullable" to false),
      "status" to mapOf("type" to "enum", "enum" to listOf("OPEN", "PAID")),
      "items" to mapOf("type" to "list", "items" to mapOf("type" to "object", "properties" to mapOf(
        "name" to mapOf("type" to "text"),
        "qty" to mapOf("type" to "number", "integer" to true),
        "price" to mapOf("type" to "number"),
        "serials" to mapOf("type" to "text"),
        "id" to mapOf("type" to "text")
      ))),
      "id" to mapOf("type" to "text"),
      "createdAt" to mapOf("type" to "datetime"),
      "updatedAt" to mapOf("type" to "datetime")
    ))
  }

  @Test fun `answers are converted to json text without nulls`() {
    val response = CloudFlareClient.Response("clairic-27b", mapOf(
      "number" to CloudFlareClient.Answer(value = "INV-1"),
      "amount" to CloudFlareClient.Answer(value = 10.5),
      "missing" to CloudFlareClient.Answer()
    ), usage = CloudFlareClient.Usage(655, 0))

    expect(response.toGenericResponse()).toEqual(AIClient.Response(null, "success", "clairic-27b",
      """{"number":"INV-1","amount":10.5}""", AIClient.TokenUsage("clairic-27b", 655, 0)))
  }

  @Test fun `row list properties are split and filled`() {
    val response = CloudFlareClient.Response("clairic-27b", mapOf(
      "items" to CloudFlareClient.Answer(value = listOf(
        mapOf("name" to "A", "qty" to 1, "price" to 2, "serials" to "S1, S2"),
        mapOf("name" to "B", "qty" to 1, "price" to 2)
      ))
    ), usage = CloudFlareClient.Usage(1, 0))

    expect(response.toGenericResponse(typeOf<Invoice>().toJsonSchema()).text).toEqual(
      """{"items":[{"name":"A","qty":1,"price":2,"serials":["S1","S2"]},{"name":"B","qty":1,"price":2,"serials":[]}]}""")
  }

  @Test fun `truncated response is reported as such`() {
    val response = CloudFlareClient.Response("clairic-27b", emptyMap(), truncated = true, usage = CloudFlareClient.Usage(1, 0))

    expect(response.toGenericResponse().status).toEqual("truncated")
  }

  @Test fun `data url is passed as embedded media`() {
    expect(URI("data:image/png;base64,AAAA").toClairicMedia())
      .toEqual(mapOf("content_type" to "image/png", "base64" to "AAAA"))
  }

  @Test fun `workers ai response is parsed`() {
    val envelope = JsonMapper().parse<CloudFlareClient.Envelope>("""{"result":{"model":"clairic-27b",
      "answers":{"number":{"type":"text","value":"INV-1","p_null":0.0}},"truncated":false,
      "usage":{"state_tokens":61,"input_tokens":10,"output_tokens":0,"passes":1}},"success":true}""")

    expect(envelope.result.answers["number"]?.value).toEqual("INV-1")
    expect(envelope.result.usage).toEqual(CloudFlareClient.Usage(10, 0))
  }

  @Test fun `extraction uses typed client when it supports it`() {
    expect(DataExtractor(TypedAIClient()).extract<TestClass>("test").key).toEqual("typed")
  }

  @Test fun `extraction excludes defaulted system fields from the typed schema`() {
    val client = TypedAIClient("""{"number": "1", "items": []}""")

    DataExtractor(client).extract<Tracked>("test")

    val properties = client.schema["properties"] as Map<*, *>
    expect(properties.keys).toEqual(setOf("number", "items"))
    expect(((properties["items"] as Map<*, *>)["items"] as Map<*, *>)["properties"])
      .toEqual(mapOf(
        "name" to mapOf("type" to "string"),
        "serials" to mapOf("type" to "array", "items" to mapOf("type" to "string"))
      ))
  }

  @Test fun `extraction keeps system fields without default values in the typed schema`() {
    val client = TypedAIClient("""{"id": "1"}""")

    DataExtractor(client).extract<Identified>("test")

    expect((client.schema["properties"] as Map<*, *>).keys).toEqual(setOf("id"))
  }

  enum class Status { OPEN, PAID }
  data class Invoice(
    val number: String,
    val seller: String?,
    val date: LocalDate,
    val amount: Decimal,
    val status: Status?,
    val items: List<Item>,
    val id: String = "1",
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant? = null
  ) {
    data class Item(val name: String, val qty: Int, val price: Decimal, val serials: List<String>, val id: String = "1")
  }

  data class Identified(val id: String)
  data class Tracked(val number: String, val items: List<Item>, val id: String = "1",
                     val createdAt: Instant = Instant.now(), val updatedAt: Instant? = null)
  data class Item(val name: String, val serials: List<String>, val id: String = "1")

  data class TestClass(val key: String)
}

private class TypedAIClient(private val text: String = """{"key": "typed"}"""): AIClient, TypedExtractionClient {
  lateinit var schema: Map<String, Any?>

  override fun query(input: String, vararg fileUrl: URI, prevResponseId: String?, params: Node) =
    AIClient.Response(null, "success", "test-model", """{"key": "queried"}""")

  override fun stream(input: String, vararg fileUrl: URI, params: Node) = emptySequence<String>()

  override fun extractTyped(state: String, schema: Map<String, Any?>, vararg fileUrl: URI, params: Node): AIClient.Response {
    this.schema = schema
    return AIClient.Response(null, "success", "test-model", text)
  }
}
