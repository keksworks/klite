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

private data class PropInfo(val path: String, val prop: KProperty1<*, *>, val isCollection: Boolean, val elemType: KClass<*>?)

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
        val attrs = mutableMapOf<String, String>()
        for (i in 0 until attributes.length) {
          val attrName = "@${attributes.getLocalName(i) ?: attributes.getQName(i)}"
          attrs[attrName] = attributes.getValue(i)
          current[attrName] = attributes.getValue(i)
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
    val props = type.readProps()
    val values = mutableMapOf<String, Any>()
    val collectedItems = mutableMapOf<String, MutableList<Any>>()
    val complexProps = props.filter { v -> v.value.isCollection && v.value.elemType != null && !Converter.supports(v.value.elemType!!) }

    parseSax(xml, onEnd = { current, parent, path, text ->
      if (text.isNotEmpty()) current[""] = text

      // Complex collection: create typed object from accumulated children
      for ((collPath, info) in complexProps) {
        if (path.endsWith("/$collPath") || path == "/$collPath") {
          val elemValues = mutableMapOf<String, Any>()
          current.forEach { (k, v) -> if (k.isNotEmpty()) elemValues[k] = v }
          if (text.isNotEmpty()) elemValues[""] = text
          collectedItems.getOrPut(info.path) { mutableListOf() }.add(
            buildObject(elemValues, info.elemType!!, info.elemType.readProps()))
          if (parent != null) parent[path.substringAfterLast("/")] = current.toMap()
          return@parseSax
        }
      }

      // Simple collection: accumulate text values
      for ((propPath, info) in props) {
        if (info.isCollection && matchPath(path, propPath) && text.isNotEmpty()) {
          (values.getOrPut(propPath) { mutableListOf<String>() } as MutableCollection<String>).add(text)
          return@parseSax
        }
      }

      // Simple property: extract text or attribute value
      for ((propPath, info) in props) {
        if (info.isCollection) continue
        if (propPath.contains("/@")) {
          val (elemName, attrKey) = propPath.split("/@", limit = 2)
          if (path.endsWith("/$elemName") && current.containsKey("@$attrKey")) {
            values[propPath] = current["@$attrKey"]!!
          }
        } else if (text.isNotEmpty() && matchPath(path, propPath)) {
          values[propPath] = text
        }
      }

      // Propagate to parent
      if (parent != null) {
        val name = path.substringAfterLast("/")
        val existing = parent[name]
        if (existing == null) parent[name] = if (text.isNotEmpty() && current.size <= 1) text else current.toMap()
        else parent[name] = when {
          existing is MutableList<*> -> (existing as MutableList<Any>).apply { add(current.toMap()) }
          existing is Map<*, *> && current.size <= 1 && text.isNotEmpty() -> mutableListOf(existing, text)
          existing is String && text.isNotEmpty() && current.size <= 1 -> mutableListOf(existing, text)
          else -> existing
        }
      }
    })

    collectedItems.forEach { (path, items) -> values[path] = items }
    return buildObject(values, type, props)
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
          if (existing == null) parent[name] = textNode
          else parent[name] = when (existing) {
            is MutableCollection<*> -> (existing as MutableCollection<Any>).apply { add(textNode) }
            is String -> mutableListOf(existing, textNode)
            else -> existing
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

  private fun KClass<*>.readProps(): Map<String, PropInfo> = publicProperties.values
    .mapNotNull { prop ->
      val path = prop.findAnnotation<XmlPath>()?.path ?: prop.name
      val propType = prop.returnType.classifier as? KClass<*> ?: return@mapNotNull null
      val isCol = propType.isSubclassOf(Collection::class)
      val elemType = if (isCol) prop.returnType.arguments.first().type?.classifier as? KClass<*> else null
      path to PropInfo(path, prop, isCol, elemType)
    }.toMap()

  private fun matchPath(fullPath: String, path: String): Boolean =
    fullPath == path || fullPath.endsWith("/$path") || (!path.startsWith("/") && fullPath.endsWith(path))

  private fun <T: Any> buildObject(values: Map<String, Any>, type: KClass<T>, props: Map<String, PropInfo>): T {
    val constructorArgs = mutableMapOf<String, Any?>()
    for ((_, info) in props) {
      val value = values[info.path] ?: continue
      if (info.isCollection) {
        val list = if (value is Collection<*>) value.map { it.toString() } else listOf(value.toString())
        constructorArgs[info.prop.name] = if (info.elemType != null && Converter.supports(info.elemType))
          list.map { Converter.from(it, info.elemType) } else value
      } else if (value is Map<*, *>) {
        val nestedType = info.prop.returnType.classifier as KClass<*>
        constructorArgs[info.prop.name] = buildObject(value as XmlNode, nestedType, nestedType.readProps())
      } else {
        constructorArgs[info.prop.name] = Converter.from(value.toString(), info.prop.returnType)
      }
    }
    return type.createFrom(constructorArgs)
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
