package klite.xml

import klite.*
import klite.nodes.Node
import org.intellij.lang.annotations.Language
import org.xml.sax.InputSource
import java.io.InputStream
import java.io.Reader
import java.io.StringReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamConstants.*
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.PROPERTY
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubclassOf

/** Supports absolute (from root /) and relative paths (resolved from current element), empty path means current element, attributes start with @ */
@Target(PROPERTY) @Retention(RUNTIME)
annotation class XmlPath(val path: String)

private data class PropInfo(val path: String, val prop: KProperty1<*, *>) {
  val type = prop.returnType.classifier as? KClass<*>
  val isCollection = type?.isSubclassOf(Collection::class) == true
  val elemType = if (isCollection) prop.returnType.arguments.first().type?.classifier as? KClass<*> else null
}

/** Intermediate XML representation; text excludes child element text */
internal class XmlElement(
  val name: String,
  val attributes: Map<String, String>,
  @JvmField var text: String = "",
  @JvmField var children: MutableList<XmlElement> = mutableListOf()
)

@Deprecated("Use XmlParser instead", ReplaceWith("XmlParser"))
typealias XMLParser = XmlParser

@Suppress("UNCHECKED_CAST")
class XmlParser(
  private val factory: XMLInputFactory = XMLInputFactory.newFactory().apply {
    setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
    setProperty(XMLInputFactory.SUPPORT_DTD, false)
  },
  private val keys: KeyConverter = KeyConverter(),
  private val values: ValueConverter<Any?> = ValueConverter()
) {
  fun parsePathMap(@Language("xml") xml: InputStream, filter: ((String) -> Boolean)? = null) = parsePathMap(InputSource(xml), filter)
  fun parsePathMap(@Language("xml") xml: Reader, filter: ((String) -> Boolean)? = null) = parsePathMap(InputSource(xml), filter)
  fun parsePathMap(@Language("xml") xml: String, filter: ((String) -> Boolean)? = null) = parsePathMap(InputSource(StringReader(xml)), filter)

  internal fun parsePathMap(@Language("xml") xml: InputSource, filter: ((String) -> Boolean)? = null): XmlNode {
    val result = mutableMapOf<String, Any?>()
    val r = reader(xml)
    data class Frame(val path: String, val text: StringBuilder = StringBuilder())
    val stack = mutableListOf<Frame>()
    while (r.hasNext()) {
      when (r.next()) {
        START_ELEMENT -> {
          val name = r.localName.takeIf(String::isNotEmpty) ?: r.name.toString()
          val path = if (stack.isEmpty()) "/${keys.from(name)}" else "${stack.last().path}/${keys.from(name)}"
          stack += Frame(path)
          for (i in 0 until r.attributeCount) {
            val attrName = r.getAttributeLocalName(i).takeIf(String::isNotEmpty) ?: r.getAttributeName(i).toString()
            val attrPath = "$path/${keys.from("@${attrName.removePrefix("@")}")}"
            if (filter == null || filter(attrPath)) result[attrPath] = r.getAttributeValue(i)
          }
        }
        CHARACTERS, CDATA ->
          stack.lastOrNull()?.text?.append(r.text)
        XMLStreamConstants.END_ELEMENT -> {
          val frame = stack.removeLast()
          val text = frame.text.toString().trim()
          if (text.isNotEmpty() && (filter == null || filter(frame.path))) result[frame.path] = values.from(text)
        }
      }
    }
    r.close()
    return result
  }

  inline fun <reified T: Any> parse(@Language("xml") xml: InputStream): T = parse(InputSource(xml), T::class)
  inline fun <reified T: Any> parse(@Language("xml") xml: Reader): T = parse(InputSource(xml), T::class)
  inline fun <reified T: Any> parse(@Language("xml") xml: String): T = parse(StringReader(xml))

  fun <T : Any> parse(@Language("xml") xml: InputSource, type: KClass<T>): T {
    val root = readElement(xml)
    return buildObject(root, type, root)
  }

  fun parseNodes(xml: InputStream) = parseNodes(InputSource(xml))
  fun parseNodes(xml: Reader) = parseNodes(InputSource(xml))
  fun parseNodes(xml: String) = parseNodes(StringReader(xml))

  internal fun parseNodes(xml: InputSource): XmlNode {
    fun toNode(element: XmlElement): MutableMap<String, Any?> {
      val node = mutableMapOf<String, Any?>()
      if (element.text.isNotEmpty()) node[""] = element.text
      element.attributes.forEach { e -> node[keys.from(e.key)] = e.value }
      for (child in element.children) {
        val childName = keys.from(child.name)
        if (child.children.isEmpty() && child.text.isNotEmpty()) {
          val existing = node[childName]
          node[childName] = when (existing) {
            null -> child.text
            is MutableList<*> -> (existing as MutableList<Any?>).apply { add(child.text) }
            else -> mutableListOf(existing, child.text)
          }
          child.attributes.forEach { (k, v) -> node["$childName${keys.from(k)}"] = v }
        } else {
          val childNode = toNode(child)
          val existing = node[childName]
          node[childName] = when (existing) {
            null -> childNode
            is MutableList<*> -> (existing as MutableList<Any?>).apply { add(childNode) }
            else -> mutableListOf(existing, childNode)
          }
        }
      }
      return node
    }

    val root = readElement(xml)
    val rootNode = toNode(root)
    if (rootNode.size > 1) rootNode.remove("")
    return mapOf(keys.from(root.name) to if (rootNode.size == 1) rootNode[""] ?: rootNode else rootNode)
  }

  private fun reader(xml: InputSource) =
    xml.characterStream?.let { factory.createXMLStreamReader(it) } ?: factory.createXMLStreamReader(xml.byteStream)

  internal fun readElement(xml: InputSource): XmlElement {
    val reader = reader(xml)
    val textBuf = StringBuilder()
    var root: XmlElement? = null
    val stack = mutableListOf<XmlElement>()

    while (reader.hasNext()) {
      val event = reader.next()
      if (event == START_ELEMENT) {
        stack += XmlElement(
          reader.localName.takeIf(String::isNotEmpty) ?: reader.name.toString(),
          (0 until reader.attributeCount).associate { i ->
            val attrName = reader.getAttributeLocalName(i).takeIf(String::isNotEmpty) ?: reader.getAttributeName(i).toString()
            "@${attrName.removePrefix("@")}" to reader.getAttributeValue(i)
          }
        )
      } else if (event == CHARACTERS || event == CDATA) {
        textBuf.append(reader.text)
      } else if (event == END_ELEMENT) {
        val element = stack.removeLast()
        element.text = textBuf.toString().trim()
        textBuf.setLength(0)
        stack.lastOrNull()?.children?.add(element) ?: run { root = element }
      }
    }
    reader.close()
    return requireNotNull(root) { "XML document has no root element" }
  }

  private fun XmlElement.values(path: String, root: XmlElement = this): List<Any> {
    if (path.isEmpty()) return listOf(text)
    val parts = path.trim('/').split('/').filter(String::isNotEmpty)
    if (parts.size == 1 && parts.first().startsWith("@")) return attributes[parts.first()].let(::listOfNotNull)

    var current = listOf(if (path.startsWith("/")) root else this)
    for (part in parts) {
      if (current.isEmpty()) return emptyList()
      if (part.startsWith("@")) return current.flatMap { it.attributes[part].let(::listOfNotNull) }
      current = current.flatMap { e -> if (e.name == part) listOf(e) else e.children.filter { it.name == part } }
    }
    return current
  }

  private fun <T: Any> buildObject(element: XmlElement, type: KClass<T>, root: XmlElement): T {
    val constructorArgs = mutableMapOf<String, Any?>()
    for (prop in type.publicProperties.values) {
      val info = PropInfo(prop.findAnnotation<XmlPath>()?.path ?: keys.to(prop.name), prop)
      val rawValues = element.values(info.path, root)
      if (rawValues.isEmpty()) continue
      val kType = info.prop.returnType
      if (info.isCollection) {
        constructorArgs[info.prop.name] = rawValues.map { value(it, kType.arguments.firstOrNull()?.type, info.elemType, root) }
      } else {
        rawValues.lastOrNull()?.let { constructorArgs[info.prop.name] = value(it, kType, info.type, root) }
      }
    }
    return type.createFrom(constructorArgs)
  }

  private fun value(raw: Any, type: KType?, classifier: KClass<*>?, root: XmlElement): Any {
    val text = (raw as? XmlElement)?.text ?: raw
    val converted = values.from(text, type)
    if (converted !== text) return converted ?: text
    if (raw is XmlElement && classifier != null && !Converter.supports(classifier)) return buildObject(raw, classifier, root)
    return type?.let { Converter.from(text.toString(), it) } ?: text
  }
}

typealias XmlNode = Node
