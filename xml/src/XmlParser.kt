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
  private val propInfoCache = ConcurrentHashMap<KClass<*>, Collection<PropInfo>>()

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

  inline fun <reified T: Any> parse(@Language("xml") xml: InputStream): T = parse(InputSource(xml), T::class)
  inline fun <reified T: Any> parse(@Language("xml") xml: Reader): T = parse(InputSource(xml), T::class)
  inline fun <reified T: Any> parse(@Language("xml") xml: String): T = parse(StringReader(xml))

  fun <T : Any> parse(@Language("xml") xml: InputSource, type: KClass<T>): T {
    val root = readElement(xml)
    return buildObject(root, type, root.name, root)
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
    val text = StringBuilder()
    var root: XmlElement? = null
    val stack = mutableListOf<XmlElement>()

    while (reader.hasNext()) {
      val event = reader.next()
      when (event) {
        START_ELEMENT -> {
          stack += XmlElement(
            reader.tagName,
            (0 until reader.attributeCount).associate { i ->
              "@${reader.attrName(i)}" to reader.getAttributeValue(i)
            }
          )
        }
        CHARACTERS, CDATA -> text.append(reader.text)
        END_ELEMENT -> {
          val element = stack.removeLast()
          element.text = text.trim().toString()
          text.setLength(0)
          stack.lastOrNull()?.children?.add(element) ?: run { root = element }
        }
      }
    }
    reader.close()
    return root!!
  }

  private fun values(path: String, currentPath: String, element: XmlElement, root: XmlElement): Pair<List<Any>, List<String>> {
    if (path.isEmpty()) return listOf(element) to listOf(currentPath)
    val parts = path.trim('/').split('/').filter(String::isNotEmpty)
    if (parts.size == 1 && parts.first().startsWith("@")) return element.attributes[parts.first()].let(::listOfNotNull) to emptyList()

    // Determine start: absolute paths start from root, relative from current element
    val startElement: XmlElement
    val startPath: String
    val startIdx: Int
    if (path.startsWith("/")) {
      startElement = root; startPath = root.name; startIdx = 1
    } else if (parts.first() == currentPath) {
      startElement = element; startPath = currentPath; startIdx = 1
    } else {
      startElement = element; startPath = currentPath; startIdx = 0
    }

    // Tree-walk from start, tracking full paths
    var current: List<XmlElement> = listOf(startElement)
    var currentPaths: List<String> = listOf(startPath)
    for (i in startIdx until parts.size) {
      if (current.isEmpty()) return emptyList<Any>() to emptyList()
      val part = parts[i]
      if (part.startsWith("@")) return current.flatMap { it.attributes[part].let(::listOfNotNull) } to emptyList()
      val next = mutableListOf<XmlElement>()
      val nextPaths = mutableListOf<String>()
      for (j in current.indices) {
        for (child in current[j].children) {
          if (child.name == part) {
            next.add(child)
            nextPaths.add("${currentPaths[j]}/${child.name}")
          }
        }
      }
      current = next
      currentPaths = nextPaths
    }
    return current as List<Any> to currentPaths
  }

  private fun <T: Any> buildObject(element: XmlElement, type: KClass<T>, currentPath: String, root: XmlElement): T {
    val props = propInfoCache.getOrPut(type) {
      type.publicProperties.values.map { PropInfo(it.findAnnotation<XmlPath>()?.path ?: keys.to(it.name), it) }
    }
    val constructorArgs = mutableMapOf<String, Any?>()
    for (info in props) {
      val kType = info.prop.returnType
      val (rawValues, paths) = values(info.path, currentPath, element, root)
      if (rawValues.isEmpty()) continue
      if (info.isCollection) {
        constructorArgs[info.prop.name] = rawValues.indices.map { i -> value(rawValues[i], paths.getOrNull(i), kType.arguments.firstOrNull()?.type, info.elemType, root) }
      } else {
        val last = rawValues.lastIndex
        constructorArgs[info.prop.name] = value(rawValues[last], paths.getOrNull(last), kType, info.type, root)
      }
    }
    return type.createFrom(constructorArgs)
  }

  private fun value(raw: Any, rawPath: String?, type: KType?, classifier: KClass<*>?, root: XmlElement): Any {
    val text = (raw as? XmlElement)?.text ?: raw
    if (text is String) {
      val converted = values.from(text, type)
      if (converted !== text) return converted ?: text
    }
    if (classifier != null && Converter.supports(classifier)) return Converter.from(text.toString(), type!!) ?: text
    if (raw is XmlElement && classifier != null) return buildObject(raw, classifier, rawPath ?: raw.name, root)
    return text
  }

  private val XMLStreamReader.tagName: String
    get() = localName.takeIf(String::isNotEmpty) ?: name.toString()

  private fun XMLStreamReader.attrName(i: Int): String =
    getAttributeLocalName(i)?.takeIf(String::isNotEmpty) ?: getAttributeName(i).toString()
}

typealias XmlNode = Node
