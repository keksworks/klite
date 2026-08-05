package klite.xml

import org.junit.jupiter.api.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.util.function.Consumer
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.system.measureNanoTime

data class BenchItem(@XmlPath("@id") val id: Int, val name: String, val description: String, val active: Boolean)
data class BenchCatalog(@XmlPath("catalog/item") val items: List<BenchItem>)

class XmlBenchmarkTest {
  private val blackhole = Consumer<Any> { }
  private val xml = buildString {
    append("<catalog>")
    repeat(50) { i ->
      append("<item id=\"$i\">")
      append("<name>Item $i</name>")
      append("<description>A moderately long description for item $i with some extra text to make it realistic</description>")
      append("<active>${i % 2 == 0}</active>")
      append("</item>")
    }
    append("</catalog>")
  }

  private val parser = XmlParser()
  private val domFactory = DocumentBuilderFactory.newInstance().apply {
    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    setFeature("http://xml.org/sax/features/external-general-entities", false)
    setFeature("http://xml.org/sax/features/external-parameter-entities", false)
  }
  private val domBuilder = domFactory.newDocumentBuilder()

  @Test fun benchmark() {
    val warmup = 10_000
    val rounds = 50_000

    repeat(warmup) { domParse(xml) }
    repeat(warmup) { parser.parsePathMap(xml) }
    repeat(warmup) { parser.parseNodes(xml) }
    repeat(warmup) { parser.readElement(InputSource(StringReader(xml))) }
    repeat(warmup) { parser.parse<BenchCatalog>(xml) }

    Thread.sleep(3000)

    val domNs = measureNanoTime { repeat(rounds) { domParse(xml) } }
    println("DOM: ${domNs / 1_000_000} ms  (${domNs / rounds} ns/op)")

    val pathsNs = measureNanoTime { repeat(rounds) { parser.parsePathMap(xml) } }
    println("parsePathMap: ${pathsNs / 1_000_000} ms  (${pathsNs / rounds} ns/op), ${"%.2f".format(domNs.toDouble() / pathsNs)}x vs DOM")

    val nodesNs = measureNanoTime { repeat(rounds) { parser.parseNodes(xml) } }
    println("parseNodes: ${nodesNs / 1_000_000} ms  (${nodesNs / rounds} ns/op), ${"%.2f".format(domNs.toDouble() / nodesNs)}x vs DOM")

    val readElementNs = measureNanoTime { repeat(rounds) { parser.readElement(InputSource(StringReader(xml))) } }
    println("readElement: ${readElementNs / 1_000_000} ms  (${readElementNs / rounds} ns/op), ${"%.2f".format(domNs.toDouble() / readElementNs)}x vs DOM")

    val typedNs = measureNanoTime { repeat(rounds) { parser.parse<BenchCatalog>(xml) } }
    println("parse<Typed>: ${typedNs / 1_000_000} ms  (${typedNs / rounds} ns/op), ${"%.2f".format(domNs.toDouble() / typedNs)}x vs DOM")
  }

  private fun domParse(xml: String): Document {
    val doc = domBuilder.parse(InputSource(StringReader(xml)))
    val items = doc.getElementsByTagName("item")
    for (i in 0 until items.length) {
      val el = items.item(i) as Element
      blackhole.accept(el.getAttribute("id"))
      blackhole.accept(el.getElementsByTagName("name").item(0).textContent)
      blackhole.accept(el.getElementsByTagName("description").item(0).textContent)
      blackhole.accept(el.getElementsByTagName("active").item(0).textContent)
    }
    return doc
  }
}
