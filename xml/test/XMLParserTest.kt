package klite.xml

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import klite.SnakeCase
import klite.ValueConverter
import klite.nodes.at
import klite.nodes.nodes
import klite.nodes.text
import klite.nodes.value
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

  data class Item(@XmlPath("@id") val id: String, val name: String)
  data class Container(@XmlPath("root/item") val items: List<Item>)

  @Test fun `duplicate complex children at nested level become list`() {
    @Language("XML") val xml = """
      <root>
        <item id="1"><name>First</name></item>
        <item id="2"><name>Second</name></item>
        <item id="3"><name>Third</name></item>
      </root>
    """.trimIndent()
    val result = parser.parse<Container>(xml.byteInputStream())
    expect(result.items).toEqual(listOf(
      Item("1", "First"),
      Item("2", "Second"),
      Item("3", "Third"),
    ))
  }

  data class Author(val name: String)
  data class BookWithAuthors(
    @XmlPath("@id") val id: Int,
    val title: String,
    @XmlPath("author") val authors: List<Author>
  )
  data class LibraryWithAuthors(@XmlPath("library/book") val books: List<BookWithAuthors>)

  @Test fun `complex collection elements are recursively built`() {
    @Language("XML") val xml = """
      <library>
        <book id="1">
          <title>The Hobbit</title>
          <author><name>Tolkien</name></author>
        </book>
        <book id="2">
          <title>Dune</title>
          <author><name>Herbert</name></author>
        </book>
      </library>
    """.trimIndent()
    val result = parser.parse<LibraryWithAuthors>(xml.byteInputStream())
    expect(result.books).toEqual(listOf(
      BookWithAuthors(1, "The Hobbit", listOf(Author("Tolkien"))),
      BookWithAuthors(2, "Dune", listOf(Author("Herbert"))),
    ))
  }

  data class OnlyChild(val name: String)
  data class SingleComplexList(@XmlPath("root/child") val children: List<OnlyChild>)

  @Test fun `single complex child in list is wrapped correctly`() {
    @Language("XML") val xml = """<root><child><name>Only</name></child></root>"""
    val result = parser.parse<SingleComplexList>(xml.byteInputStream())
    expect(result.children).toEqual(listOf(OnlyChild("Only")))
  }

  data class TextOnlyId(@XmlPath("") val value: String)
  data class ParentWithTextChild(
    @XmlPath("@requestId") val requestId: String,
    val id: TextOnlyId,
    @XmlPath("tag") val tags: List<String>
  )

  @Test fun `text-only element mapped to complex type`() {
    @Language("XML") val xml = """
      <root requestId="req-1">
        <id>ABC-123</id>
        <tag>a</tag>
      </root>
    """.trimIndent()
    val result = parser.parse<ParentWithTextChild>(xml.byteInputStream())
    expect(result.requestId).toEqual("req-1")
    expect(result.id).toEqual(TextOnlyId("ABC-123"))
    expect(result.tags).toEqual(listOf("a"))
  }

  data class AttrAndTextId(@XmlPath("") val value: String, @XmlPath("@scheme") val scheme: String?)
  data class ParentWithAttrChild(
    val id: AttrAndTextId,
    @XmlPath("tag") val tags: List<String>
  )

  @Test fun `element with text and attribute mapped to complex type`() {
    @Language("XML") val xml = """
      <root>
        <id scheme="ISO">XYZ-789</id>
        <tag>x</tag>
        <tag>y</tag>
      </root>
    """.trimIndent()
    val result = parser.parse<ParentWithAttrChild>(xml.byteInputStream())
    expect(result.id).toEqual(AttrAndTextId("XYZ-789", "ISO"))
    expect(result.tags).toEqual(listOf("x", "y"))
  }

  data class DeepNestedId(@XmlPath("") val value: String, @XmlPath("@type") val type: String? = null)
  data class InnerItem(val id: DeepNestedId, val description: String)
  data class OuterItem(@XmlPath("@id") val id: Int, val inner: InnerItem)
  data class DeepRoot(@XmlPath("root/item") val items: List<OuterItem>)

  @Test fun `deeply nested complex collections with text-only children`() {
    @Language("XML") val xml = """
      <root>
        <item id="1">
          <inner>
            <id type="primary">AAA</id>
            <description>First item</description>
          </inner>
        </item>
        <item id="2">
          <inner>
            <id>BBB</id>
            <description>Second item</description>
          </inner>
        </item>
      </root>
    """.trimIndent()
    val result = parser.parse<DeepRoot>(xml.byteInputStream())
    expect(result.items).toEqual(listOf(
      OuterItem(1, InnerItem(DeepNestedId("AAA", "primary"), "First item")),
      OuterItem(2, InnerItem(DeepNestedId("BBB"), "Second item")),
    ))
  }

  data class SnakeProps(
    val myValue: String,
    @XmlPath("@dataType") val dataType: String
  )

  @Test fun `key converter transforms element and attribute names`() {
    val snakeParser = XMLParser(keys = SnakeCase)
    @Language("XML") val xml = """
      <root>
        <my_value data_type="test">hello</my_value>
      </root>
    """.trimIndent()
    val result = snakeParser.parse<SnakeProps>(xml.byteInputStream())
    expect(result.myValue).toEqual("hello")
    expect(result.dataType).toEqual("test")
  }

  data class CustomValueProps(
    val count: Int,
    val label: String
  )

  @Test fun `value converter transforms text content`() {
    val upperValuesParser = XMLParser(values = object : ValueConverter<Any?>() {
      override fun from(o: Any?): Any? = when (o) {
        is String -> o.uppercase()
        else -> o
      }
    })
    @Language("XML") val xml = """<root><count>5</count><label>hello</label></root>"""
    val result = upperValuesParser.parse<CustomValueProps>(xml.byteInputStream())
    expect(result.count).toEqual(5)
    expect(result.label).toEqual("HELLO")
  }

  data class CombinedChild(val itemName: String, val description: String)

  @Test fun `both key and value converters together`() {
    val customParser = XMLParser(
      keys = SnakeCase,
      values = object : ValueConverter<Any?>() {
        override fun from(o: Any?): Any? = when (o) {
          is String -> o.reversed()
          else -> o
        }
      }
    )
    @Language("XML") val xml = """
      <root>
        <item>
          <item_name>The Hobbit</item_name>
          <description>A classic</description>
        </item>
      </root>
    """.trimIndent()
    val result = customParser.parse<CombinedChild>(xml.byteInputStream())
    expect(result.itemName).toEqual("tibboH ehT")
    expect(result.description).toEqual("cissalc A")
  }

  data class PathMapChild(val itemName: String)

  @Test fun `key converter with nested elements`() {
    val snakeParser = XMLParser(keys = SnakeCase)
    @Language("XML") val xml = """<root><item><item_name>value</item_name></item></root>"""
    val result = snakeParser.parse<PathMapChild>(xml.byteInputStream())
    expect(result.itemName).toEqual("value")
  }
}
