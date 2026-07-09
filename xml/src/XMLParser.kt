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
                       onStart: (path: String, attrs: Map<String, String>) -> Unit = { _, _ -> },
                       onEnd: (path: String, text: String, attrs: Map<String, String>) -> Unit = { _, _, _ -> }) {
    var path = ""
    val text = StringBuilder()
    val paths = mutableListOf<String>()
    val attrsList = mutableListOf<MutableMap<String, String>>()

    factory.newSAXParser().parse(xml, object : DefaultHandler() {
      override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
        val name = localName ?: qName
        paths.add(name)
        path = "/${paths.joinToString("/")}"
        text.setLength(0)
        val attrs = mutableMapOf<String, String>()
        for (i in 0 until attributes.length) {
          attrs["@${attributes.getLocalName(i) ?: attributes.getQName(i)}"] = attributes.getValue(i)
        }
        attrsList.add(attrs)
        onStart(path, attrs)
      }

      override fun characters(ch: CharArray, start: Int, length: Int) {
        text.append(ch, start, length)
      }

      override fun endElement(uri: String?, localName: String?, qName: String) {
        val trimmed = text.toString().trim()
        val attrs = attrsList.removeAt(attrsList.lastIndex)
        onEnd(path, trimmed, attrs)
        paths.removeAt(paths.lastIndex)
        path = if (paths.isEmpty()) "" else "/${paths.joinToString("/")}"
        text.setLength(0)
      }
    })
  }

  fun parse(xml: InputStream, callback: (parentPath: String, name: String, text: String) -> Unit) {
    parseSax(xml, onEnd = { path, text, attrs ->
      if (text.isNotEmpty()) callback(path.substringBeforeLast("/", ""), path.substringAfterLast("/"), text)
      attrs.forEach { (name, value) -> callback(path, name, value) }
    })
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
    val collectionTargets = mutableMapOf<String, Pair<XmlPathMeta, KClass<*>>>()

    for ((path, meta) in metaMap) {
      if (!meta.isCollection || meta.property == null) continue
      val listType = meta.property.returnType.arguments.first().type?.classifier as? KClass<*>
      if (listType != null && !Converter.supports(listType)) collectionTargets[path] = meta to listType
    }

    // elementStack: tracks accumulated values for each nesting level
    val stack = mutableListOf(mutableMapOf<String, Any>())
    val collectedItems = mutableMapOf<String, MutableList<Any>>()

    parseSax(xml,
      onStart = { _, attrs ->
        stack.add(mutableMapOf())
        attrs.forEach { (k, v) -> stack.last()[k] = v }
      },
      onEnd = { path, text, _ ->
        val elemValues = stack.removeAt(stack.lastIndex)
        if (text.isNotEmpty()) elemValues[""] = text

        // Check if this element should become a typed object
        for ((collPath, pair) in collectionTargets) {
          if (path.endsWith(collPath)) {
            val (meta, listType) = pair
            val item = createFromValues(elemValues, listType, listType.readXmlAnnotationsMeta())
            collectedItems.getOrPut(meta.path) { mutableListOf() }.add(item)
            // Also add to parent so simple values propagate
            if (stack.isNotEmpty()) stack.last()[path.substringAfterLast("/")] = elemValues.toMap()
            return@parseSax
          }
        }

        // Propagate to parent
        if (stack.isNotEmpty()) {
          val name = path.substringAfterLast("/")
          val parent = stack.last()
          val existing = parent[name]
          if (existing == null) parent[name] = if (text.isNotEmpty() && elemValues.size <= 1) text else elemValues.toMap()
          else {
            val map = elemValues.toMap()
            parent[name] = when (existing) {
              is MutableList<*> -> (existing as MutableList<Any>).apply { add(map) }
              is Map<*, *> -> mutableListOf(existing, map)
              else -> existing
            }
          }
        }
      }
    )

    for ((path, meta) in metaMap) {
      if (!meta.isCollection || collectionTargets.containsKey(path)) continue
    }

    for ((path, meta) in metaMap) {
      if (!meta.isCollection || collectionTargets.containsKey(path) || meta.property == null) continue
      val listType = meta.property.returnType.arguments.first().type?.classifier as? KClass<*>
      if (listType != null && Converter.supports(listType)) {
        val texts = mutableListOf<String>()
        parseSax(xml) { elemPath, text, _ ->
          if (text.isNotEmpty() && elemPath.endsWith(path)) texts.add(text)
        }
        result[meta.property.name] = texts.map { Converter.from(it, listType) }
      }
    }

    for ((path, meta) in metaMap) {
      if (meta.property == null) continue
      if (meta.isCollection && !collectionTargets.containsKey(path) && !result.containsKey(meta.property.name)) {
        continue
      }
      if (!meta.isCollection) continue
      val collected = collectedItems[path]
      if (collected != null) result[meta.property.name] = collected
    }

    return type.createFrom(result)
  }

  fun parseNodes(xml: InputStream): XmlNode {
    data class ElemState(val children: MutableMap<String, Any> = mutableMapOf(),
                         val text: StringBuilder = StringBuilder(),
                         val attrs: MutableMap<String, String> = mutableMapOf())
    val stack = mutableListOf<ElemState>()

    parseSax(xml,
      onStart = { _, attrs -> stack.add(ElemState(attrs = attrs.toMutableMap())) },
      onEnd = { path, text, _ ->
        val state = stack.removeAt(stack.lastIndex)
        if (text.isNotEmpty()) state.text.append(text)
        val elemText = state.text.toString().trim()

        val result = mutableMapOf<String, Any>()
        if (elemText.isNotEmpty()) result[""] = elemText
        result.putAll(state.children)
        result.putAll(state.attrs)

        if (stack.isNotEmpty()) {
          val name = path.substringAfterLast("/")
          val parent = stack.last().children
          val textNode = result.remove("")
          if (textNode != null) {
            parent[name] = textNode
            result.forEach { (k, v) -> parent["$name$k"] = v }
          } else when (val existing = parent[name]) {
            null -> parent[name] = result
            is MutableList<*> -> (existing as MutableList<Any>).add(result)
            else -> parent[name] = mutableListOf(existing, result)
          }
        } else {
          // Root element
          val name = path.substringAfterLast("/")
          stack.add(ElemState(children = mutableMapOf(name to result)))
        }
      }
    )

    return if (stack.isNotEmpty()) stack.first().children.toMap() else emptyMap()
  }

  private fun KClass<*>.readXmlAnnotationsMeta(): Map<String, XmlPathMeta> = publicProperties.values
    .mapNotNull { prop -> prop.findAnnotation<XmlPath>()?.let { ann ->
        ann.path to XmlPathMeta(ann.path, prop)
    }}.toMap()

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
      if (meta.isCollection && value is List<*>) {
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
