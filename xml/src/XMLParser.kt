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
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubclassOf

@Target(PROPERTY) @Retention(RUNTIME)
annotation class XmlPath(
  /** root element starts with /, path suffix without /, attributes with /@ */
  val path: String
)

private data class XmlPathMeta(val path: String, val isCollection: Boolean, val propertyName: String)

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

  fun <T : Any> parse(xml: InputStream, type: KClass<T>): T {
    val metaMap = type.readXmlAnnotationsMeta()

    val hasComplexCollections = metaMap.values.any { meta ->
      meta.isCollection && run {
        val prop = type.publicProperties[meta.propertyName]
        val listType = prop?.returnType?.arguments?.first()?.type?.classifier as? KClass<*>
        listType != null && !Converter.supports(listType)
      }
    }

    if (hasComplexCollections) return parseWithNodes(xml, type, metaMap)

    // Pre-compute relative path lookups for O(1) access
    val relativePaths = metaMap.entries
      .filter { (key, _) -> !key.startsWith("/") }
      .associate { (key, meta) -> key to meta }

    val values = mutableMapOf<String, Any>()
    parse(xml) { parentPath, name, text ->
      val meta = metaMap.find(parentPath, name, relativePaths)
      if (meta.isCollection) {
        (values.getOrPut(meta.path) { mutableListOf<String>() } as MutableList<String>).add(text)
      } else {
        values[meta.path] = text
      }
    }

    return createFromValues(values, type, metaMap)
  }

  private fun <T: Any> parseWithNodes(xml: InputStream, type: KClass<T>, metaMap: Map<String, XmlPathMeta>): T {
    val nodes = parseNodes(xml)
    val props = type.publicProperties
    val constructorArgs = mutableMapOf<String, Any?>()

    for ((_, meta) in metaMap) {
      val prop = props[meta.propertyName] ?: continue
      if (!meta.isCollection) continue

      val listType = prop.returnType.arguments.first().type?.classifier as? KClass<*> ?: continue
      val listMetaMap = listType.readXmlAnnotationsMeta()
      val pathParts = meta.path.split("/").filter { it.isNotEmpty() }

      // Navigate the node tree to find the collection
      var currentNode: Any = nodes
      for (part in pathParts) {
        currentNode = (currentNode as? Map<*, *>)?.get(part) ?: break
      }

      if (currentNode is List<*>) {
        constructorArgs[meta.propertyName] = currentNode.map { item ->
          when (item) {
            is Map<*, *> -> {
              val itemValues = mutableMapOf<String, Any>()
              item.forEach { (k, v) -> if (k is String) itemValues[k] = v ?: "" }
              createFromValues(itemValues, listType, listMetaMap)
            }
            else -> Converter.from(item.toString(), listType)
          }
        }
      }
    }

    return type.createFrom(constructorArgs)
  }

  private fun KClass<*>.readXmlAnnotationsMeta(): Map<String, XmlPathMeta> = publicProperties.values
    .mapNotNull { prop ->
      prop.findAnnotation<XmlPath>()?.let { ann ->
        val returnClass = prop.returnType.classifier as? KClass<*>
        val isCollection = returnClass?.isSubclassOf(Collection::class) == true
        ann.path to XmlPathMeta(ann.path, isCollection, prop.name)
      }
    }.toMap()

  private fun Map<String, XmlPathMeta>.find(parentPath: String, name: String,
                                            relativePaths: Map<String, XmlPathMeta> = emptyMap()): XmlPathMeta {
    val fullPath = "$parentPath/$name"
    return this[fullPath] ?: this[name] ?:
      relativePaths.entries.firstOrNull { (key, _) -> fullPath.endsWith(key) }?.value ?:
      XmlPathMeta(fullPath, false, "")
  }

  private fun <T: Any> createFromValues(values: Map<String, Any>, type: KClass<T>, metaMap: Map<String, XmlPathMeta>): T {
    val props = type.publicProperties
    val constructorArgs = mutableMapOf<String, Any?>()

    for ((_, meta) in metaMap) {
      val prop = props[meta.propertyName] ?: continue
      val value = values[meta.path]

      if (meta.isCollection && value is List<*>) {
        val listType = prop.returnType.arguments.first().type?.classifier as? KClass<*>
        if (listType != null && Converter.supports(listType)) {
          constructorArgs[meta.propertyName] = value.map { Converter.from(it.toString(), listType) }
        } else {
          constructorArgs[meta.propertyName] = value
        }
      } else if (value != null) {
        constructorArgs[meta.propertyName] = Converter.from(value.toString(), prop.returnType)
      }
    }

    return type.createFrom(constructorArgs)
  }

  fun parsePathMap(xml: InputStream): Map<String, String> {
    val result = mutableMapOf<String, String>()
    parse(xml) { parentPath, name, text ->
      result["$parentPath/$name"] = text
    }
    return result
  }

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
              child.forEach { if (it.key != "") children[name + it.key] = it.value }
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
            val attrs = start.attributes.asSequence().associate { "@${it.name.localPart}" to it.value }
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
