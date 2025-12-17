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
    </library>
  """.trimIndent().byteInputStream()

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
        mapOf("id" to "1", "title" to "The Hobbit", "author" to "Tolkien"),
        mapOf("id" to "2", "title" to "Dune", "author" to "Herbert")
      )
    ))

    expect(library.nodes("book").first().text("title")).toEqual("The Hobbit")
    expect(library.nodes("book").last().text("id")).toEqual("2")
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
}
