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
    val metaMap = type.readXmlAnnotationsMeta()
    val hasComplexCollections = metaMap.values.any { meta ->
      meta.isCollection && meta.property != null && run {
        val listType = meta.property.returnType.arguments.first().type?.classifier as? KClass<*>
        listType != null && !Converter.supports(listType)
      }
    }

    if (hasComplexCollections) return parseComplex(xml, type, metaMap)

    val values = mutableMapOf<String, Any>()
    parse(xml) { parentPath, name, text ->
      val meta = metaMap.findMeta(parentPath, name)
      if (meta.isCollection) (values.getOrPut(meta.path) { mutableListOf<String>() } as MutableCollection<String>).add(text)
      else values[meta.path] = text
    }
    return createFromValues(values, type, metaMap)
  }

  private fun <T: Any> parseComplex(xml: InputStream, type: KClass<T>, metaMap: Map<String, XmlPathMeta>): T {
    val result = mutableMapOf<String, Any?>()
    val complexTargets = mutableMapOf<String, Pair<XmlPathMeta, KClass<*>>>()
    val simpleTargets = mutableMapOf<String, Pair<XmlPathMeta, KClass<*>>>()

    for ((path, meta) in metaMap) {
      if (!meta.isCollection || meta.property == null) continue
      val listType = meta.property.returnType.arguments.first().type?.classifier as? KClass<*> ?: continue
      if (Converter.supports(listType)) simpleTargets[path] = meta to listType
      else complexTargets[path] = meta to listType
    }

    val collectedItems = mutableMapOf<String, MutableList<Any>>()
    val simpleTexts = mutableMapOf<String, MutableList<String>>()

    parseSax(xml) { current, parent, path, text ->
      if (text.isNotEmpty()) current[""] = text

      // Complex collection: create typed object
      for ((collPath, pair) in complexTargets) {
        if (path.endsWith(collPath)) {
          val (meta, listType) = pair
          collectedItems.getOrPut(meta.path) { mutableListOf() }.add(
            createFromValues(current, listType, listType.readXmlAnnotationsMeta()))
          if (parent != null) parent[path.substringAfterLast("/")] = current.toMap()
          return@parseSax
        }
      }

      // Simple collection: collect text
      for ((collPath, pair) in simpleTargets) {
        if (path.endsWith(collPath) && text.isNotEmpty()) {
          simpleTexts.getOrPut(pair.first.path) { mutableListOf() }.add(text)
          return@parseSax
        }
      }

      // Propagate to parent
      if (parent != null) {
        val name = path.substringAfterLast("/")
        val existing = parent[name]
        if (existing == null) parent[name] = if (text.isNotEmpty() && current.size <= 1) text else current.toMap()
        else {
          val map = current.toMap()
          parent[name] = when (existing) {
            is MutableList<*> -> (existing as MutableList<Any>).apply { add(map) }
            is Map<*, *> -> mutableListOf(existing, map)
            else -> existing
          }
        }
      }
    }

    for ((_, meta) in metaMap) {
      if (meta.property == null || !meta.isCollection) continue
      collectedItems[meta.path]?.let { result[meta.property.name] = it }
      simpleTexts[meta.path]?.let { texts ->
        val listType = meta.property.returnType.arguments.first().type?.classifier as? KClass<*>
        result[meta.property.name] = if (listType != null) texts.map { Converter.from(it, listType) } else texts
      }
    }

    return type.createFrom(result)
  }

  fun parseNodes(xml: InputStream): XmlNode {
    var root: MutableMap<String, Any>? = null

    parseSax(xml) { current, parent, path, text ->
      if (text.isNotEmpty()) current[""] = text

      if (parent != null) {
        val name = path.substringAfterLast("/")
        val textNode = current.remove("")
        if (textNode != null) {
          parent[name] = textNode
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

  private fun <T: Any> createFromValues(values: Map<String, Any>, type: KClass<T>, metaMap: Map<String, XmlPathMeta>): T {
    val constructorArgs = mutableMapOf<String, Any?>()
    for ((_, meta) in metaMap) {
      val prop = meta.property ?: continue
      val value = values[meta.path]
      if (meta.isCollection && value is Collection<*>) {
        val listType = prop.returnType.arguments.first().type?.classifier as? KClass<*>
        constructorArgs[prop.name] = if (listType != null && Converter.supports(listType))
          value.map { Converter.from(it.toString(), listType) } else value
      } else if (value != null) {
        constructorArgs[prop.name] = Converter.from(value.toString(), prop.returnType)
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
