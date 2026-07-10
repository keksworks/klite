package klite.xml

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

class XMLParserTest {
  val parser = XMLParser()

  @Language("XML")
  val xml = """
      <transportMovement>
        <id schemeAgencyId="AGENCY1">123</id>
        <modeCode>SEA</modeCode>
        <dangerousGoodsIndicator>true</dangerousGoodsIndicator>
      </transportMovement>
    """.trimIndent().byteInputStream()

  @Language("XML")
  val xmlWithNamespaces = """
      <x:transportMovement xmlns:x="http://example.com/x" xmlns:z="http://example.com/z">
        <z:id z:schemeAgencyId="AGENCY1">123</z:id>
        <z:modeCode>SEA</z:modeCode>
        <x:dangerousGoodsIndicator>true</x:dangerousGoodsIndicator>
      </x:transportMovement>
    """.trimIndent().byteInputStream()

  @Language("XML")
  val xmlWithRepeating = """
    <library>
      <book id="1">
        <title>The Hobbit</title>
        <author>Tolkien</author>
      </book>
      <book id="2">
        <title>Dune</title>
        <author>Herbert</author>
      </book>
      <book id="12">
        <title>12 Chairs</title>
        <author>Ilf</author>
        <author>Petrov</author>
      </book>
    </library>
  """.byteInputStream()

  @Test fun parse() {
    expect(parser.parse<Identifier>(xml)).toEqual(
      Identifier("123", "AGENCY1", "SEA", dangerousGoods = true))
  }

  @Test fun namespaces() {
    expect(parser.parse<Identifier>(xmlWithNamespaces)).toEqual(
      Identifier("123", "AGENCY1", "SEA", dangerousGoods = true))
  }

  @Test fun parsePathMap() {
    expect(parser.parsePathMap(xmlWithNamespaces)).toEqual(
      mapOf(
        "/transportMovement/id" to "123",
        "/transportMovement/id/@schemeAgencyId" to "AGENCY1",
        "/transportMovement/modeCode" to "SEA",
        "/transportMovement/dangerousGoodsIndicator" to "true",
      ))
  }

  @Test fun parseNodes() {
    val result = parser.parseNodes(xmlWithNamespaces)
    expect(result).toEqual(
      mapOf("transportMovement" to mapOf(
        "id" to "123",
        "id@schemeAgencyId" to "AGENCY1",
        "modeCode" to "SEA",
        "dangerousGoodsIndicator" to "true",
      )))
    expect(result.at("transportMovement").value<Boolean>("dangerousGoodsIndicator")).toEqual(true)
  }

  @Test fun parseNodesWithRepeating() {
    val library = parser.parseNodes(xmlWithRepeating).at("library")
    expect(library).toEqual(mapOf(
      "book" to listOf(
        mapOf("@id" to "1", "title" to "The Hobbit", "author" to "Tolkien"),
        mapOf("@id" to "2", "title" to "Dune", "author" to "Herbert"),
        mapOf("@id" to "12", "title" to "12 Chairs", "author" to listOf("Ilf", "Petrov"))
      )
    ))

    expect(library.nodes("book").first().text("title")).toEqual("The Hobbit")
    expect(library.nodes("book").last().text("@id")).toEqual("12")
  }

  data class Identifier(
    @XmlPath("/transportMovement/id") // from root
    val id: String,

    @XmlPath("id/@schemeAgencyId") // relative
    val type: String,

    @XmlPath("modeCode")
    val mode: String,

    @XmlPath("dangerousGoodsIndicator")
    val dangerousGoods: Boolean = false
  )

  @Language("XML")
  val xmlWithRepeatedSimple = """
    <library>
      <book>The Hobbit</book>
      <book>Dune</book>
      <book>Narnia</book>
    </library>
  """.trimIndent().byteInputStream()

  data class SimpleLibrary(
    @XmlPath("library/book") val books: List<String>
  )

  @Test fun parseWithRepeatedElements() {
    val result = parser.parse<SimpleLibrary>(xmlWithRepeatedSimple)
    expect(result.books).toEqual(listOf("The Hobbit", "Dune", "Narnia"))
  }

  data class Book(
    @XmlPath("@id") val id: Int,
    val title: String,
    @XmlPath("author") val authors: List<String>
  )

  data class Library(
    @XmlPath("library/book") val books: List<Book>
  )

  @Test fun parseWithNestedRepeatedElements() {
    val result = parser.parse<Library>(xmlWithRepeating)
    expect(result.books).toEqual(listOf(
      Book(1, "The Hobbit", listOf("Tolkien")),
      Book(2, "Dune", listOf("Herbert")),
      Book(12, "12 Chairs", listOf("Ilf", "Petrov")),
    ))
  }

  @Test fun `xml with root attributes`() {
    data class RootAttrs(@XmlPath("@id") val id: Int, @XmlPath("@name") val name: String)
    @Language("XML") val xml = """<item id="42" name="test"/>"""
    val result = parser.parse<RootAttrs>(xml.byteInputStream())
    expect(result.id).toEqual(42)
    expect(result.name).toEqual("test")
  }

  data class NestedItem(val value: String)
  data class RootWithNested(
    @XmlPath("@requestId") val requestId: String,
    val item: NestedItem,
    @XmlPath("tag") val tags: List<String>
  )

  @Test fun `xml with nested objects`() {
    @Language("XML") val xml = """
      <root requestId="req-1">
        <item><value>hello</value></item>
        <tag>a</tag>
        <tag>b</tag>
      </root>
    """
    val result = parser.parse<RootWithNested>(xml.byteInputStream())
    expect(result.requestId).toEqual("req-1")
    expect(result.item).toEqual(NestedItem("hello"))
    expect(result.tags).toEqual(listOf("a", "b"))
  }

  @Test fun `nested default values`() {
    data class Inner(val x: String, val y: String = "default")
    data class RootWithDefaults(@XmlPath("inner") val inner: Inner)
    @Language("XML") val xml = """<root><inner><x>1</x></inner></root>"""
    val result = parser.parse<RootWithDefaults>(xml.byteInputStream())
    expect(result.inner.x).toEqual("1")
    expect(result.inner.y).toEqual("default")
  }
}
