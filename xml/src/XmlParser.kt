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

  fun parsePathMap(@Language("xml") xml: InputStream, filter: ((Path) -> Boolean)? = null) = parsePathMap(InputSource(xml), filter)
  fun parsePathMap(@Language("xml") xml: Reader, filter: ((Path) -> Boolean)? = null) = parsePathMap(InputSource(xml), filter)
  fun parsePathMap(@Language("xml") xml: String, filter: ((Path) -> Boolean)? = null) = parsePathMap(InputSource(StringReader(xml)), filter)

  internal fun parsePathMap(@Language("xml") xml: InputSource, filter: ((Path) -> Boolean)? = null): Map<Path, Any?> {
    val result = mutableMapOf<Path, Any?>()
    val occurrences = mutableMapOf<Path, Int>()

    fun indexedPath(path: Path): Path {
      val count = (occurrences[path] ?: 0) + 1
      occurrences[path] = count
      return if (count == 1) path else Path(path.tags.map { if (it == path.tags.last()) it.copy(index = count) else it })
    }

    val r = reader(xml)
    data class Frame(val path: Path, val text: StringBuilder = StringBuilder())
    val stack = mutableListOf<Frame>()
    while (r.hasNext()) {
      when (r.next()) {
        START_ELEMENT -> {
          val name = r.localName.takeIf(String::isNotEmpty) ?: r.name.toString()
          val basePath = if (stack.isEmpty()) Path(listOf(Path.Tag(keys.from(name)))) else stack.last().path + Path.Tag(keys.from(name))
          val indexed = indexedPath(basePath)
          stack += Frame(indexed)
          for (i in 0 until r.attributeCount) {
            val attrName = r.getAttributeLocalName(i).takeIf(String::isNotEmpty) ?: r.getAttributeName(i).toString()
            val attrPath = indexed + Path.Tag(keys.from("@${attrName.removePrefix("@")}"))
            if (filter == null || filter(attrPath)) result[attrPath] = r.getAttributeValue(i)
          }
        }
        CHARACTERS, CDATA ->
          stack.lastOrNull()?.text?.append(r.text)
        END_ELEMENT -> {
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
}

data class Path(val tags: List<Tag>) {
  data class Tag(val name: String, val index: Int = 0) {
    override fun toString() = if (index > 0) "${name}#${index}" else name
  }

  constructor(path: String): this(path.trim('/').split('/').map { part ->
    val hashIdx = part.indexOf('#')
    if (hashIdx >= 0) Tag(part.substring(0, hashIdx), part.substring(hashIdx + 1).toInt()) else Tag(part)
  })

  operator fun plus(tag: Tag) = Path(tags + tag)
  fun endsWith(tag: String) = tags.lastOrNull()?.name == tag
  override fun toString() = tags.joinToString("/", prefix = "/")
}

typealias XmlNode = Node
