package klite.xml

import klite.*
import klite.nodes.Node
import org.intellij.lang.annotations.Language
import org.xml.sax.InputSource
import java.io.InputStream
import java.io.Reader
import java.io.StringReader
import java.util.concurrent.ConcurrentHashMap
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants.*
import javax.xml.stream.XMLStreamReader
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.PROPERTY
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubclassOf

/** Supports absolute (from root /) and relative paths (resolved from current element), empty path means current element, attributes start with @ */
@Target(PROPERTY) @Retention(RUNTIME)
annotation class XmlPath(val path: String)

@Deprecated("Use XmlParser instead", ReplaceWith("XmlParser"))
typealias XMLParser = XmlParser

typealias XmlNode = Node

@Suppress("UNCHECKED_CAST")
class XmlParser(
  private val factory: XMLInputFactory = XMLInputFactory.newFactory().apply {
    setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
    setProperty(XMLInputFactory.SUPPORT_DTD, false)
  },
  private val keys: KeyConverter = KeyConverter(),
  private val values: ValueConverter<Any?> = ValueConverter()
) {
  private val propInfoCache = ConcurrentHashMap<KClass<*>, List<PropInfo>>()

  fun parsePathMap(@Language("xml") xml: InputStream, filter: ((String) -> Boolean)? = null) = parsePathMap(InputSource(xml), filter)
  fun parsePathMap(@Language("xml") xml: Reader, filter: ((String) -> Boolean)? = null) = parsePathMap(InputSource(xml), filter)
  fun parsePathMap(@Language("xml") xml: String, filter: ((String) -> Boolean)? = null) = parsePathMap(InputSource(StringReader(xml)), filter)

  internal fun parsePathMap(@Language("xml") xml: InputSource, filter: ((String) -> Boolean)? = null): XmlNode {
    val result = LinkedHashMap<String, Any?>(256)
    val occurrences = HashMap<String, Int>(256)

    fun indexedPath(path: String): String {
      val count = (occurrences[path] ?: 0) + 1
      occurrences[path] = count
      return if (count == 1) path else "$path#$count"
    }

    val r = reader(xml)
    var path = ""
    val text = StringBuilder()
    while (r.hasNext()) {
      when (r.next()) {
        START_ELEMENT -> {
          path = indexedPath("$path/${keys.from(r.tagName)}")
          for (i in 0 until r.attributeCount) {
            val attrPath = "$path/${keys.from("@${r.attrName(i)}")}"
            if (filter == null || filter(attrPath)) result[attrPath] = r.getAttributeValue(i)
          }
        }
        CHARACTERS, CDATA -> text.append(r.text)
        END_ELEMENT -> {
          val trimmed = text.trim()
          if (trimmed.isNotEmpty() && (filter == null || filter(path))) result[path] = values.from(trimmed.toString())
          path = path.substringBeforeLast("/")
          text.setLength(0)
        }
      }
    }
    r.close()
    return result
  }

  fun parseNodes(xml: InputStream) = parseNodes(InputSource(xml))
  fun parseNodes(xml: Reader) = parseNodes(InputSource(xml))
  fun parseNodes(xml: String) = parseNodes(StringReader(xml))

  internal fun parseNodes(xml: InputSource): XmlNode {
    val root = readElement(xml)
    val rootNode = toNode(root)
    if (rootNode.size > 1) rootNode.remove("")
    return mapOf(keys.from(root.name) to if (rootNode.size == 1) rootNode[""] ?: rootNode else rootNode)
  }

  private fun toNode(element: XmlElement): MutableMap<String, Any?> {
    val node = mutableMapOf<String, Any?>()
    if (element.text != null) node[""] = element.text
    element.attributes?.forEach { (k, v) -> node[keys.from(k)] = v }
    for (child in element.children) {
      val childName = keys.from(child.name)
      if (child.children.isEmpty() && child.text != null) {
        val existing = node[childName]
        node[childName] = when (existing) {
          null -> child.text
          is MutableList<*> -> (existing as MutableList<Any?>).apply { add(child.text) }
          else -> mutableListOf(existing, child.text)
        }
        child.attributes?.forEach { (k, v) -> node["$childName${keys.from(k)}"] = v }
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

  private fun reader(xml: InputSource) =
    xml.characterStream?.let { factory.createXMLStreamReader(it) } ?: factory.createXMLStreamReader(xml.byteStream)

  inline fun <reified T: Any> parse(@Language("xml") xml: InputStream): T = parse(InputSource(xml), T::class)
  inline fun <reified T: Any> parse(@Language("xml") xml: Reader): T = parse(InputSource(xml), T::class)
  inline fun <reified T: Any> parse(@Language("xml") xml: String): T = parse(StringReader(xml))

  fun <T : Any> parse(@Language("xml") xml: InputSource, cls: KClass<T>): T {
    val root = readElement(xml)
    return buildObject(root, cls.createType()) as T
  }

  internal fun readElement(xml: InputSource): XmlElement {
    val reader = reader(xml)
    val text = StringBuilder(256)
    var root: XmlElement? = null
    val stack = mutableListOf<XmlElement>()

    while (reader.hasNext()) {
      when (reader.next()) {
        START_ELEMENT -> {
          stack += XmlElement(
            reader.tagName,
            if (reader.attributeCount == 0) null else (0 until reader.attributeCount).associate { i ->
              "@${reader.attrName(i)}" to reader.getAttributeValue(i)
            }
          )
        }
        CHARACTERS, CDATA -> text.append(reader.text)
        END_ELEMENT -> {
          val element = stack.removeLast()
          element.text = text.trim().takeIf { it.isNotEmpty() }?.toString()
          text.setLength(0)
          val parent = stack.lastOrNull()
          parent?.children?.add(element) ?: run { root = element }
        }
      }
    }
    reader.close()
    return root!!
  }

  private fun buildObject(element: XmlElement, type: KType): Any? {
    // TODO: better null handling
    val cls = type.classifier as KClass<*>
    if (cls == String::class) return values.from(element.text)
    else if (Converter.supports(cls)) return values.from(Converter.from(element.text!!, cls))
    val converted = values.from(element.text, type)
    if (converted != element.text) return converted

    val props = propInfoCache.getOrPut(cls) {
      cls.publicProperties.values.map {
        val path = it.findAnnotation<XmlPath>()?.path?.trim('/')?.split('/') ?: listOf(keys.to(it.name))
        PropInfo(path, it)
      }
    }
    val args = HashMap<String, Any?>()
    for (p in props) {
      val els = element.find(p.path)
      args[p.name] = when {
        els.isEmpty() -> null
        p.elemType != null -> els.map { buildObject(it, p.elemType) }
        else -> buildObject(els.first(), p.type)
      }
    }
    return cls.createFrom(args)
  }

  private val XMLStreamReader.tagName: String
    get() = localName.takeIf(String::isNotEmpty) ?: name.toString()

  private fun XMLStreamReader.attrName(i: Int): String =
    getAttributeLocalName(i)?.takeIf(String::isNotEmpty) ?: getAttributeName(i).toString()
}

private class PropInfo(val path: List<String>, val prop: KProperty1<*, *>) {
  val name get() = prop.name
  val type get() = prop.returnType
  val isCollection get() = (type.classifier as? KClass<*>)?.isSubclassOf(Collection::class) == true
  val elemType = if (isCollection) prop.returnType.arguments.first().type else null
}

internal class XmlElement(
  @JvmField val name: String,
  @JvmField val attributes: Map<String, String>? = null,
  @JvmField var text: String? = null,
  @JvmField var children: MutableList<XmlElement> = mutableListOf()
) {
  fun find(path: List<String>): List<XmlElement> {
    var e: XmlElement = this
    for (i in path.indices) {
      val n = path[i]
      if (n == e.name) continue
      else if (n == "") return listOf(e)
      else if (n.startsWith("@")) return listOf(XmlElement(n, text = e.attributes?.get(n)))
      val els = e.children.filter { it.name == n }
      if (i == path.lastIndex) return els
      else e = els.firstOrNull() ?: return emptyList()
    }
    error("No way")
  }
}
