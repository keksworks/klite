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

private data class PropInfo(val path: String, val prop: KProperty1<*, *>) {
  val type = prop.returnType.classifier as? KClass<*>
  val isCollection = type?.isSubclassOf(Collection::class) == true
  val elemType = if (isCollection) prop.returnType.arguments.first().type?.classifier as? KClass<*> else null
}

private data class XmlElement(
  val name: String,
  val attributes: Map<String, String>,
  val text: String,
  val children: List<XmlElement>
)

@Deprecated("Use XmlParser instead", ReplaceWith("XmlParser"))
typealias XMLParser = XmlParser

@Suppress("UNCHECKED_CAST")
class XmlParser(
  private val factory: SAXParserFactory = SAXParserFactory.newInstance().apply {
    isNamespaceAware = true
    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
  },
  private val keys: KeyConverter = KeyConverter(),
  private val values: ValueConverter<Any?> = ValueConverter()
) {
  private fun parseSax(@Language("xml") xml: InputSource,
                       callback: (current: MutableMap<String, Any>, parent: MutableMap<String, Any>?, path: String, text: String) -> Unit) {
    val text = StringBuilder()
    val paths = mutableListOf<String>()
    val stack = mutableListOf<MutableMap<String, Any>>()

    factory.newSAXParser().parse(xml, object : DefaultHandler() {
      override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
        val tag = keys.from(localName ?: qName)
        paths.add(tag)
        text.setLength(0)
        val current = mutableMapOf<String, Any>()
        for (i in 0 until attributes.length) {
          val attrName = "@" + (attributes.getLocalName(i) ?: attributes.getQName(i))
          current[keys.from(attrName)] = attributes.getValue(i)
        }
        stack.add(current)
      }

      override fun characters(ch: CharArray, start: Int, length: Int) {
        text.append(ch, start, length)
      }

      override fun endElement(uri: String?, localName: String?, qName: String) {
        val trimmed = text.toString().trim()
        val current = stack.removeLast()
        val parent = stack.lastOrNull()
        val path = "/${paths.joinToString("/")}"
        current.entries.toList().forEach { (name, value) -> if (name.startsWith("@") && value is String) callback(current, parent, "$path/$name", value) }
        callback(current, parent, path, trimmed)
        paths.removeLast()
        text.setLength(0)
      }
    })
  }

  internal fun parse(@Language("xml") xml: InputSource, callback: (parentPath: String, name: String, text: Any?) -> Unit) {
    parseSax(xml) { current, _, path, text ->
      if (text.isNotEmpty()) callback(path.substringBeforeLast("/", ""), path.substringAfterLast("/"), this@XmlParser.values.from(text))
      current.forEach { (name, value) -> if (!name.startsWith("@") && value is String) callback(path, name, value) }
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
    return buildObject(readElement(xml), type)
  }

  fun parseNodes(xml: InputStream) = parseNodes(InputSource(xml))
  fun parseNodes(xml: Reader) = parseNodes(InputSource(xml))
  fun parseNodes(xml: String) = parseNodes(StringReader(xml))

  internal fun parseNodes(xml: InputSource): XmlNode {
    var root: MutableMap<String, Any>? = null

    parseSax(xml) { current, parent, path, text ->
      if (path.substringAfterLast("/").startsWith("@")) return@parseSax
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

  private fun readElement(xml: InputSource): XmlElement {
    data class OpenElement(
      val name: String,
      val attributes: Map<String, String>,
      val text: StringBuilder = StringBuilder(),
      val children: MutableList<XmlElement> = mutableListOf()
    )

    var root: XmlElement? = null
    val stack = mutableListOf<OpenElement>()
    factory.newSAXParser().parse(xml, object : DefaultHandler() {
      override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
        stack += OpenElement(
          keys.from(localName ?: qName),
          (0 until attributes.length).associate { index ->
            keys.from("@" + (attributes.getLocalName(index) ?: attributes.getQName(index))) to attributes.getValue(index)
          }
        )
      }

      override fun characters(ch: CharArray, start: Int, length: Int) {
        stack.lastOrNull()?.text?.append(ch, start, length)
      }

      override fun endElement(uri: String?, localName: String?, qName: String) {
        val element = stack.removeLast().let { XmlElement(it.name, it.attributes, it.text.toString().trim(), it.children) }
        stack.lastOrNull()?.children?.add(element) ?: run { root = element }
      }
    })
    return requireNotNull(root) { "XML document has no root element" }
  }

  private fun XmlElement.values(path: String): List<Any> {
    if (path.isEmpty()) return listOf(text)
    val parts = path.trim('/').split('/').filter(String::isNotEmpty)
    if (parts.size == 1 && parts.firstOrNull()?.startsWith("@") == true) return attributes[parts.first()].let(::listOfNotNull)

    fun descendants(element: XmlElement): Sequence<XmlElement> = sequence {
      yield(element)
      element.children.forEach { yieldAll(descendants(it)) }
    }
    fun follow(element: XmlElement, remaining: List<String>): List<Any> {
      if (remaining.isEmpty()) return listOf(element)
      val part = remaining.first()
      if (part.startsWith("@")) return element.attributes[part].let(::listOfNotNull)
      return element.children.filter { it.name == part }.flatMap { follow(it, remaining.drop(1)) }
    }
    return descendants(this).filter { it.name == parts.first() }.flatMap { follow(it, parts.drop(1)) }.toList()
  }

  private fun <T: Any> buildObject(element: XmlElement, type: KClass<T>): T {
    val constructorArgs = mutableMapOf<String, Any?>()
    for (prop in type.publicProperties.values) {
      val info = PropInfo(prop.findAnnotation<XmlPath>()?.path ?: prop.name, prop)
      val rawValues = element.values(info.path)
      if (rawValues.isEmpty()) continue
      val kType = info.prop.returnType
      if (info.isCollection) {
        constructorArgs[info.prop.name] = rawValues.map { value(it, kType.arguments.firstOrNull()?.type, info.elemType) }
      } else {
        rawValues.lastOrNull()?.let { constructorArgs[info.prop.name] = value(it, kType, info.type) }
      }
    }
    return type.createFrom(constructorArgs)
  }

  private fun value(raw: Any, type: kotlin.reflect.KType?, classifier: KClass<*>?): Any {
    if (raw is XmlElement && classifier != null && !Converter.supports(classifier)) return buildObject(raw, classifier)
    val text = (raw as? XmlElement)?.text ?: raw
    val converted = values.from(text, type)
    if (converted !== text) return converted ?: text
    return type?.let { Converter.from(text.toString(), it) } ?: text
  }
}

typealias XmlNode = Node
