package klite.xml

import klite.*
import klite.nodes.Node
import org.intellij.lang.annotations.Language
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.io.Reader
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.PROPERTY
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubclassOf

/** Supports absolute and relative paths, empty path means current element, attributes start with @ */
@Target(PROPERTY) @Retention(RUNTIME)
annotation class XmlPath(val path: String)

private data class PropInfo(val path: String, val prop: KProperty1<*, *>, val isCollection: Boolean, val elemType: KClass<*>?)

@Suppress("UNCHECKED_CAST")
class XMLParser(
  private val factory: SAXParserFactory = SAXParserFactory.newInstance().apply {
    isNamespaceAware = true
    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
  },
  private val keys: KeyConverter = KeyConverter(),
  private val values: ValueConverter<Any?> = ValueConverter()
) {
  private fun parseSax(@Language("xml") xml: InputSource,
                       onEnd: (current: MutableMap<String, Any>, parent: MutableMap<String, Any>?, path: String, text: String) -> Unit = { _, _, _, _ -> }) {
    var path = ""
    val text = StringBuilder()
    val paths = mutableListOf<String>()
    val stack = mutableListOf<MutableMap<String, Any>>()

    factory.newSAXParser().parse(xml, object : DefaultHandler() {
      override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
        val name = keys.from(localName ?: qName)
        paths.add(name)
        path = "/${paths.joinToString("/")}"
        text.setLength(0)
        val current = mutableMapOf<String, Any>()
        val attrs = mutableMapOf<String, String>()
        for (i in 0 until attributes.length) {
          val rawName = "@" + (attributes.getLocalName(i) ?: attributes.getQName(i))
          val attrName = keys.from(rawName)
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

  internal fun parse(@Language("xml") xml: InputSource, callback: (parentPath: String, name: String, text: Any?) -> Unit) {
    parseSax(xml) { current, _, path, text ->
      if (text.isNotEmpty()) callback(path.substringBeforeLast("/", ""), path.substringAfterLast("/"), this@XMLParser.values.from(text))
      current.forEach { (name, value) -> if (value is String) callback(path, name, value) }
    }
  }

  fun parsePathMap(@Language("xml") xml: InputStream) = parsePathMap(InputSource(xml))
  fun parsePathMap(@Language("xml") xml: Reader) = parsePathMap(InputSource(xml))
  fun parsePathMap(@Language("xml") xml: String) = parsePathMap(StringReader(xml))

  internal fun parsePathMap(@Language("xml") xml: InputSource): XmlNode {
    val result = mutableMapOf<String, Any?>()
    parse(xml) { parentPath, name, text -> result["$parentPath/$name"] = text }
    return result
  }

  inline fun <reified T: Any> parse(@Language("xml") xml: InputStream): T = parse(InputSource(xml), T::class)
  inline fun <reified T: Any> parse(@Language("xml") xml: Reader): T = parse(InputSource(xml), T::class)
  inline fun <reified T: Any> parse(@Language("xml") xml: String): T = parse(StringReader(xml))

  fun <T : Any> parse(@Language("xml") xml: InputSource, type: KClass<T>): T {
    val props = type.readProps()
    val values = mutableMapOf<String, Any>()
    val collectedItems = mutableMapOf<String, MutableList<Any>>()
    val complexProps = props.filter { v -> v.value.isCollection && v.value.elemType != null && !Converter.supports(v.value.elemType!!) }

    parseSax(xml) { current, parent, path, text ->
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
          (values.getOrPut(propPath) { mutableListOf<Any?>() } as MutableCollection<Any?>).add(text)
          return@parseSax
        }
      }

      // Simple property: extract text or attribute value
      for ((propPath, info) in props) {
        if (info.isCollection) continue
        if (propPath.startsWith("@")) {
          val attrKey = propPath
          if (current.containsKey(attrKey)) {
            values[propPath] = current[attrKey]!!
          }
        } else if (propPath.contains("/@")) {
          val (elemName, attrKey) = propPath.split("/@", limit = 2)
          if (path.endsWith("/$elemName") && current.containsKey("@$attrKey")) {
            values[propPath] = current["@$attrKey"]!!
          }
        } else if (text.isNotEmpty() && matchPath(path, propPath)) {
          values[propPath] = text
        }
      }

      // Store root element data directly into values
      if (parent == null) {
        for ((propPath, info) in props) {
          if (info.isCollection) continue
          if (current.containsKey(propPath)) {
            val incoming = current[propPath]!!
            val propType = info.prop.returnType.classifier as? KClass<*>
            if (incoming is Map<*, *>) {
              // For complex types, always provide the full Map; for simple types, preserve converted value
              if (propType == null || !Converter.supports(propType)) values[propPath] = incoming
            } else if (values[propPath] == null) {
              values[propPath] = incoming
            }
          }
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
          existing is Map<*, *> -> mutableListOf(existing, current.toMap())
          else -> existing
        }
      }
    }

    collectedItems.forEach { (path, items) -> values[path] = items }
    return buildObject(values, type, props)
  }

  fun parseNodes(xml: InputStream) = parseNodes(InputSource(xml))
  fun parseNodes(xml: Reader) = parseNodes(InputSource(xml))
  fun parseNodes(xml: String) = parseNodes(StringReader(xml))

  internal fun parseNodes(xml: InputSource): XmlNode {
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

  private fun <T: Any> buildObject(values: XmlNode, type: KClass<T>, props: Map<String, PropInfo>): T {
    val constructorArgs = mutableMapOf<String, Any?>()
    for ((_, info) in props) {
      val rawValue = values[info.path] ?: continue
      val kType = info.prop.returnType
      if (info.isCollection) {
        val rawList = if (rawValue is Collection<*>) rawValue.toList() else listOf(rawValue)
        constructorArgs[info.prop.name] = if (info.elemType != null && Converter.supports(info.elemType))
          rawList.map { this@XMLParser.values.from(it, kType.arguments.firstOrNull()?.type) ?: it }
        else if (info.elemType != null)
          rawList.map { if (it is Map<*, *>) buildObject(it as XmlNode, info.elemType, info.elemType.readProps()) else it }
        else rawList
      } else if (rawValue is Map<*, *>) {
        val nestedType = kType.classifier as KClass<*>
        constructorArgs[info.prop.name] = buildObject(rawValue as XmlNode, nestedType, nestedType.readProps())
      } else {
        val converted = this@XMLParser.values.from(rawValue, kType)
        if (converted !== rawValue) {
          constructorArgs[info.prop.name] = converted ?: rawValue
        } else {
          val propType = kType.classifier as? KClass<*> ?: continue
          if (Converter.supports(propType))
            constructorArgs[info.prop.name] = Converter.from(rawValue.toString(), propType)
          else
            constructorArgs[info.prop.name] = buildObject(mapOf("" to rawValue) as XmlNode, propType, propType.readProps())
        }
      }
    }
    return type.createFrom(constructorArgs)
  }
}

typealias XmlNode = Node
