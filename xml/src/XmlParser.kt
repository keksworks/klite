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

private class PropInfo(rawPath: String, val prop: KProperty1<*, *>) {
  val type = prop.returnType.classifier as? KClass<*>
  val isCollection = type?.isSubclassOf(Collection::class) == true
  val elemType = if (isCollection) prop.returnType.arguments.first().type?.classifier as? KClass<*> else null
  val isEmptyPath = rawPath.isEmpty()
  val attrName: String?
  val path: Path
  init {
    val parts = rawPath.trim('/').split('/').filter(String::isNotEmpty)
    val tags = mutableListOf<Path.Tag>()
    var attr: String? = null
    for (part in parts) {
      if (part.startsWith("@")) { attr = part; break }
      tags += Path.Tag(part)
    }
    path = Path(tags)
    attrName = attr
  }
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
    return buildObject(root, type, Path(listOf(Path.Tag(root.name))))
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

  private fun findElements(path: Path, element: XmlElement): List<Pair<XmlElement, Path>> {
    val result = mutableListOf<Pair<XmlElement, Path>>()
    fun collect(el: XmlElement, tagIdx: Int, fullPath: List<Path.Tag>) {
      if (tagIdx == path.tags.size) { result.add(el to Path(fullPath)); return }
      for (child in el.children) {
        if (child.name == path.tags[tagIdx].name) collect(child, tagIdx + 1, fullPath + path.tags[tagIdx])
      }
    }
    val initTags = if (element.name == path.tags.firstOrNull()?.name && path.tags.size > 1)
      listOf(path.tags[0]) else emptyList()
    val startIdx = if (initTags.isNotEmpty()) 1 else 0
    collect(element, startIdx, initTags)
    return result
  }

  private fun <T: Any> buildObject(element: XmlElement, type: KClass<T>, currentPath: Path): T {
    val props = propInfoCache.getOrPut(type) {
      type.publicProperties.values.map { PropInfo(it.findAnnotation<XmlPath>()?.path ?: keys.to(it.name), it) }
    }
    val constructorArgs = mutableMapOf<String, Any?>()

    for (info in props) {
      if (info.isEmptyPath) {
        constructorArgs[info.prop.name] = convertText(element.text, info.prop.returnType, info.type)
        continue
      }
      val resolved = info.path

      if (info.attrName != null) {
        val matches = findElements(resolved, element)
        val attrValue = if (matches.isNotEmpty()) matches.last().first.attributes[info.attrName] else element.attributes[info.attrName]
        if (attrValue != null) constructorArgs[info.prop.name] = convertText(attrValue, info.prop.returnType, info.type)
        continue
      }
      val kType = info.prop.returnType
      if (info.isCollection) {
        val matches = findElements(resolved, element)
        if (matches.isEmpty()) continue
        val elemType = info.elemType
        constructorArgs[info.prop.name] = matches.map { (el, fullPath) ->
          if (elemType != null && !Converter.supports(elemType)) buildObject(el, elemType, fullPath)
          else convertText(el.text, kType.arguments.firstOrNull()?.type, elemType)
        }
      } else {
        val matches = findElements(resolved, element)
        if (matches.isEmpty()) continue
        val (matchEl, matchPath) = matches.last()
        val classifier = info.type
        val converted = values.from(matchEl.text, kType)
        if (converted !== matchEl.text) {
          constructorArgs[info.prop.name] = converted ?: matchEl.text
        } else if (classifier != null && !Converter.supports(classifier)) {
          constructorArgs[info.prop.name] = buildObject(matchEl, classifier, matchPath)
        } else {
          constructorArgs[info.prop.name] = convertText(matchEl.text, kType, classifier)
        }
      }
    }
    return type.createFrom(constructorArgs)
  }

  private fun convertText(text: String, type: KType?, classifier: KClass<*>?): Any? {
    if (text.isEmpty()) return null
    val converted = values.from(text, type)
    if (converted !== text) return converted
    if (classifier != null && Converter.supports(classifier)) return Converter.from(text, type!!) ?: text
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
