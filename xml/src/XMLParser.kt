package klite.xml

import klite.Converter
import klite.createFrom
import klite.publicProperties
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory
import javax.xml.stream.XMLEventReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.events.StartElement
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.PROPERTY
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation

@Target(PROPERTY) @Retention(RUNTIME)
annotation class XmlPath(
  /** root element starts with /, path suffix without /, attributes with /@ */
  val path: String
)

@Suppress("UNCHECKED_CAST")
class XMLParser(
  private val factory: SAXParserFactory = SAXParserFactory.newInstance().apply {
    isNamespaceAware = true
    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
  }
) {
  inline fun <reified T: Any> parse(xml: InputStream): T = parse(xml, T::class)

  fun parse(xml: InputStream, callback: (parentPath: String, name: String, text: String) -> Unit) {
    var currentPath = ""
    val currentText = StringBuilder()

    val handler = object : DefaultHandler() {
      override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
        val elementName = localName ?: qName
        val parentPath = currentPath
        currentPath += "/$elementName"
        currentText.setLength(0)

        for (i in 0 until attributes.length) {
          val attrName = attributes.getLocalName(i) ?: attributes.getQName(i)
          callback(currentPath, "@$attrName", attributes.getValue(i))
        }
      }

      override fun characters(ch: CharArray, start: Int, length: Int) {
        currentText.append(ch, start, length)
      }

      override fun endElement(uri: String?, localName: String?, qName: String) {
        val parentPath = currentPath.substringBeforeLast("/", "")
        val text = currentText.toString().trim()
        if (text.isNotEmpty()) {
          val elementName = localName ?: qName
          callback(parentPath, elementName, text)
        }

        currentPath = parentPath
        currentText.setLength(0)
      }
    }

    factory.newSAXParser().parse(xml, handler)
  }

  // TODO: handle repeating and nested elements
  fun <T : Any> parse(xml: InputStream, type: KClass<T>,
                      pathToProperty: Map<String, KProperty1<T, *>> = readAnnotations(type),
                      creator: (Map<String, String>) -> T = { type.createFrom(it) }
  ): T {
    val values = mutableMapOf<String, String>()
    parse(xml) { parentPath, name, text ->
      val prop = pathToProperty.find(parentPath, name)
      values[prop] = text
    }
    return creator(values)
  }

  private fun <T: Any> readAnnotations(type: KClass<T>) = type.publicProperties.values
    .mapNotNull { it.findAnnotation<XmlPath>()?.let { ann -> ann.path to it } }.toMap()

  // TODO: separate non-root path map may be pre-created for better performance
  private fun <T> Map<String, KProperty1<T, *>>.find(parentPath: String, name: String): String {
    val fullPath = "$parentPath/$name"
    return (this[fullPath] ?: this[name] ?: entries.firstOrNull { !it.key.startsWith("/") && fullPath.endsWith(it.key) }?.value)?.name ?: fullPath
  }

  fun parsePathMap(xml: InputStream): Map<String, String> =
    parse(xml, Map::class as KClass<Map<String, String>>, pathToProperty = emptyMap(), creator = { it })

  fun parseNodes(xml: InputStream): XmlNode {
    val reader = XMLInputFactory.newInstance().createXMLEventReader(xml)

    fun parseNode(reader: XMLEventReader, start: StartElement): Any {
      val children = mutableMapOf<String, Any>()
      var textContent = ""

      while (reader.hasNext()) {
        val e = reader.nextEvent()
        when {
          e.isStartElement -> {
            val childStart = e.asStartElement()
            val name = childStart.name.localPart
            val child = parseNode(reader, childStart)

            val textNode = (child as? XmlNode)?.get("")
            if (textNode != null) {
              children[name] = textNode
              child.forEach { if (it.key != "") children[name + "@" + it.key] = it.value }
            } else when (val v = children[name]) {
              null -> children[name] = child
              is MutableList<*> -> v as MutableList<Any> += child
              else -> children[name] = mutableListOf(v, child)
            }
          }
          e.isCharacters -> {
            textContent = e.asCharacters().data.trim()
          }
          e.isEndElement -> {
            val attrs = start.attributes.asSequence().associate { it.name.localPart to it.value }
            if (textContent.isEmpty()) return children + attrs
            if (children.isEmpty() && attrs.isEmpty()) return textContent
            return mapOf("" to textContent) + children + attrs
          }
        }
      }
      throw IllegalStateException()
    }

    while (reader.hasNext()) {
      val event = reader.nextEvent()
      if (event.isStartElement) {
        val element = event.asStartElement()
        return mapOf(element.name.localPart to parseNode(reader, element))
      }
    }

    return emptyMap()
  }
}

typealias XmlNode = Map<String, Any>

fun <T: Any> XmlNode.childOrNull(key: String) = get(key) as T?
fun <T: Any> XmlNode.child(key: String) = (childOrNull<T>(key) ?: throw NullPointerException("$key is absent"))

fun <T> XmlNode.children(key: String): List<T> = childOrNull<Any>(key).let {
  if (it == null) emptyList() else it as? List<T> ?: listOf(it as T)
}

fun XmlNode.at(key: String) = child<XmlNode>(key)
fun XmlNode.nodes(key: String): List<XmlNode> = children(key)
fun XmlNode.text(key: String) = child<String>(key)
fun XmlNode.textOrNull(key: String) = childOrNull<String>(key)
inline fun <reified T: Any> XmlNode.value(key: String) = Converter.from<T>(text(key))
inline fun <reified T: Any> XmlNode.valueOrNull(key: String) = textOrNull(key)?.let { Converter.from<T>(it) }
