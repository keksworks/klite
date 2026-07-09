package klite.xml

import klite.Converter
import klite.createFrom
import klite.publicProperties
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.PROPERTY
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubclassOf

@Target(PROPERTY) @Retention(RUNTIME)
annotation class XmlPath(val path: String)

private data class XmlPathMeta(val path: String, val property: KProperty1<*, *>? = null) {
  val isCollection = (property?.returnType?.classifier as? KClass<*>)?.isSubclassOf(Collection::class) == true
}

@Suppress("UNCHECKED_CAST")
class XMLParser(
  private val factory: SAXParserFactory = SAXParserFactory.newInstance().apply {
    isNamespaceAware = true
    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
  }
) {
  inline fun <reified T: Any> parse(xml: InputStream): T = parse(xml, T::class)

  private fun parseSax(xml: InputStream,
                       onEnd: (current: MutableMap<String, Any>, parent: MutableMap<String, Any>?, path: String, text: String) -> Unit = { _, _, _, _ -> }) {
    var path = ""
    val text = StringBuilder()
    val paths = mutableListOf<String>()
    val stack = mutableListOf<MutableMap<String, Any>>()

    factory.newSAXParser().parse(xml, object : DefaultHandler() {
      override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
        val name = localName ?: qName
        paths.add(name)
        path = "/${paths.joinToString("/")}"
        text.setLength(0)
        val current = mutableMapOf<String, Any>()
        for (i in 0 until attributes.length) {
          current["@${attributes.getLocalName(i) ?: attributes.getQName(i)}"] = attributes.getValue(i)
        }
        stack.add(current)
      }

      override fun characters(ch: CharArray, start: Int, length: Int) {
        text.append(ch, start, length)
      }

      override fun endElement(uri: String?, localName: String?, qName: String) {
        val trimmed = text.toString().trim()
        val current = stack.removeAt(stack.lastIndex)
        val parent = stack.lastOrNull()
        onEnd(current, parent, path, trimmed)
        paths.removeAt(paths.lastIndex)
        path = if (paths.isEmpty()) "" else "/${paths.joinToString("/")}"
        text.setLength(0)
      }
    })
  }

  fun parse(xml: InputStream, callback: (parentPath: String, name: String, text: String) -> Unit) {
    parseSax(xml) { current, _, path, text ->
      if (text.isNotEmpty()) callback(path.substringBeforeLast("/", ""), path.substringAfterLast("/"), text)
      current.forEach { (name, value) -> if (value is String) callback(path, name, value) }
    }
  }

  fun parsePathMap(xml: InputStream): Map<String, String> {
    val result = mutableMapOf<String, String>()
    parse(xml) { parentPath, name, text -> result["$parentPath/$name"] = text }
    return result
  }

  fun <T : Any> parse(xml: InputStream, type: KClass<T>): T {
    val nodes = parseNodes(xml)
    val metaMap = type.readXmlAnnotationsMeta()
    for ((_, meta) in metaMap) {
      if (meta.property == null) continue
      val value = navigatePath(nodes, meta.path)
    }
    return nodeToObject(nodes, type, metaMap)
  }

  private fun navigatePath(nodes: XmlNode, path: String): Any? {
    // Try direct lookup first (handles attribute paths like "id/@schemeAgencyId" → "id@schemeAgencyId")
    val flattened = path.replace("/@", "@")
    nodes[flattened]?.let { return it }

    // Try path-based navigation
    val parts = path.split("/").filter { it.isNotEmpty() }
    var current: Any = nodes
    for (part in parts) {
      if (part.startsWith("@")) {
        val attrName = part.substring(1)
        val map = current as? Map<*, *> ?: return null
        current = map.entries.firstOrNull { it.key.toString().endsWith("@$attrName") }?.value ?: return null
      } else {
        current = (current as? Map<*, *>)?.get(part) ?: return null
      }
    }
    return current
  }

  // For relative paths, search recursively in nested maps
  private fun findValue(nodes: XmlNode, path: String): Any? {
    navigatePath(nodes, path)?.let { return it }
    // Search in nested maps for relative paths
    if (!path.startsWith("/")) {
      for ((_, v) in nodes) {
        if (v is Map<*, *>) {
          @Suppress("UNCHECKED_CAST")
          findValue(v as XmlNode, path)?.let { return it }
        }
      }
    }
    return null
  }

  private fun <T: Any> nodeToObject(nodes: XmlNode, type: KClass<T>, metaMap: Map<String, XmlPathMeta>): T {
    val constructorArgs = mutableMapOf<String, Any?>()

    for ((_, meta) in metaMap) {
      val prop = meta.property ?: continue
      if (meta.isCollection) {
        val listType = prop.returnType.arguments.first().type?.classifier as? KClass<*>
        val rawList = findValue(nodes, meta.path)
        if (rawList is List<*>) {
          if (listType != null && Converter.supports(listType)) {
            constructorArgs[prop.name] = rawList.map { Converter.from(it.toString(), listType) }
          } else if (listType != null) {
            constructorArgs[prop.name] = rawList.map { item ->
              when (item) {
                is Map<*, *> -> nodeToObject(item as XmlNode, listType, listType.readXmlAnnotationsMeta())
                else -> Converter.from(item.toString(), listType)
              }
            }
          } else {
            constructorArgs[prop.name] = rawList
          }
        } else if (rawList != null) {
          // Single element wrapped in list
          val listType = prop.returnType.arguments.first().type?.classifier as? KClass<*>
          if (listType != null && Converter.supports(listType)) {
            constructorArgs[prop.name] = listOf(Converter.from(rawList.toString(), listType))
          } else if (listType != null) {
            constructorArgs[prop.name] = when (rawList) {
              is Map<*, *> -> listOf(nodeToObject(rawList as XmlNode, listType, listType.readXmlAnnotationsMeta()))
              else -> listOf(Converter.from(rawList.toString(), listType))
            }
          } else {
            constructorArgs[prop.name] = listOf(rawList)
          }
        }
      } else {
        val value = findValue(nodes, meta.path)
        if (value != null) constructorArgs[prop.name] = Converter.from(value.toString(), prop.returnType)
      }
    }

    return type.createFrom(constructorArgs)
  }

  fun parseNodes(xml: InputStream): XmlNode {
    var root: MutableMap<String, Any>? = null

    parseSax(xml) { current, parent, path, text ->
      if (text.isNotEmpty()) current[""] = text

      if (parent != null) {
        val name = path.substringAfterLast("/")
        val textNode = current.remove("")
        if (textNode != null) {
          val existing = parent[name]
          if (existing == null) {
            parent[name] = textNode
          } else {
            parent[name] = when (existing) {
              is MutableList<*> -> (existing as MutableList<Any>).apply { add(textNode) }
              is String -> mutableListOf(existing, textNode)
              else -> existing
            }
          }
          current.forEach { (k, v) -> parent["$name$k"] = v }
        } else when (val existing = parent[name]) {
          null -> parent[name] = current
          is MutableList<*> -> (existing as MutableList<Any>).add(current)
          else -> parent[name] = mutableListOf(existing, current)
        }
      } else {
        root = mutableMapOf(path.substringAfterLast("/") to current)
      }
    }

    return root?.toMap() ?: emptyMap()
  }

  private fun KClass<*>.readXmlAnnotationsMeta(): Map<String, XmlPathMeta> = publicProperties.values
    .associate { prop ->
      val ann = prop.findAnnotation<XmlPath>()
      val path = ann?.path ?: prop.name
      path to XmlPathMeta(path, prop)
    }

  private fun Map<String, XmlPathMeta>.findMeta(parentPath: String, name: String): XmlPathMeta {
    val fullPath = "$parentPath/$name"
    return this[fullPath] ?: this[name] ?:
      entries.firstOrNull { (key, _) -> !key.startsWith("/") && fullPath.endsWith(key) }?.value ?:
      XmlPathMeta(fullPath)
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
