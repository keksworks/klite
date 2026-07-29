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
import kotlin.reflect.full.starProjectedType

/** Supports absolute (from root /) and relative paths (resolved from current element), empty path means current element, attributes start with @ */
@Target(PROPERTY) @Retention(RUNTIME)
annotation class XmlPath(val path: String)

@Deprecated("Use XmlParser instead", ReplaceWith("XmlParser"))
typealias XMLParser = XmlParser

private class PropInfo(val prop: KProperty1<*, *>, val path: String) {
  val type = prop.returnType.classifier as? KClass<*>
  val isCollection = type?.isSubclassOf(Collection::class) == true
  val elemType = if (isCollection) prop.returnType.arguments.first().type?.classifier as? KClass<*> else null
  @JvmField val kType: KType = prop.returnType
  @JvmField val segments: List<String> = if (path.isEmpty()) emptyList() else path.trim('/').split('/').filter(String::isNotEmpty)
  @JvmField val isAbsolute = path.startsWith("/")
}

private class Tracker(@JvmField val info: PropInfo, @JvmField val segs: List<String>)

private class Resolved(
  @JvmField val childMap: Map<String, List<Tracker>>,
  @JvmField val attrNames: Array<Pair<String, PropInfo>>,
  @JvmField val textProp: PropInfo?
)

@Suppress("UNCHECKED_CAST")
class XmlParser(
  private val factory: XMLInputFactory = XMLInputFactory.newFactory().apply {
    setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
    setProperty(XMLInputFactory.SUPPORT_DTD, false)
  },
  private val keys: KeyConverter = KeyConverter(),
  private val values: ValueConverter<Any?> = ValueConverter()
) {
  private val propInfoCache = ConcurrentHashMap<KClass<*>, List<PropInfo>>()
  private val resolvedCache = ConcurrentHashMap<Pair<KClass<*>, String>, Resolved>()

  private fun propsFor(type: KClass<*>) = propInfoCache.getOrPut(type) {
    type.publicProperties.values.map { PropInfo(it, it.findAnnotation<XmlPath>()?.path ?: keys.to(it.name)) }
  }

  private fun resolve(type: KClass<*>, elemName: String) = resolvedCache.getOrPut(type to elemName) {
    val childMap = mutableMapOf<String, MutableList<Tracker>>()
    val attrNames = mutableListOf<Pair<String, PropInfo>>()
    var textProp: PropInfo? = null
    for (p in propsFor(type)) {
      val segs = effectiveSegs(p, elemName)
      when {
        segs.isEmpty() -> textProp = p
        segs.size == 1 && segs[0].startsWith("@") -> attrNames.add(segs[0].substring(1) to p)
        else -> childMap.getOrPut(segs[0]) { mutableListOf() }.add(Tracker(p, segs.subList(1, segs.size)))
      }
    }
    Resolved(childMap, attrNames.toTypedArray(), textProp)
  }

  private fun effectiveSegs(p: PropInfo, elemName: String): List<String> {
    val s = p.segments; if (s.isEmpty()) return s
    if (p.isAbsolute) return s.subList(1, s.size)
    if (s[0] == elemName) return s.subList(1, s.size)
    return s
  }

  private fun isComplex(type: KClass<*>) = type != String::class && !Converter.supports(type) && type.publicProperties.isNotEmpty()

  // ---- parsePathMap ----

  fun parsePathMap(@Language("xml") xml: InputStream, filter: ((String) -> Boolean)? = null) = parsePathMap(InputSource(xml), filter)
  fun parsePathMap(@Language("xml") xml: Reader, filter: ((String) -> Boolean)? = null) = parsePathMap(InputSource(xml), filter)
  fun parsePathMap(@Language("xml") xml: String, filter: ((String) -> Boolean)? = null) = parsePathMap(InputSource(StringReader(xml)), filter)

  internal fun parsePathMap(@Language("xml") xml: InputSource, filter: ((String) -> Boolean)? = null): XmlNode {
    val result = mutableMapOf<String, Any?>()
    val r = reader(xml)
    data class Frame(val path: String, val text: StringBuilder = StringBuilder())
    val stack = mutableListOf<Frame>()
    while (r.hasNext()) {
      when (r.next()) {
        START_ELEMENT -> {
          val name = localName(r)
          val path = if (stack.isEmpty()) "/${keys.from(name)}" else "${stack.last().path}/${keys.from(name)}"
          stack += Frame(path)
          for (i in 0 until r.attributeCount) {
            val attrPath = "$path/${keys.from("@${attrName(r, i)}")}"
            if (filter == null || filter(attrPath)) result[attrPath] = r.getAttributeValue(i)
          }
        }
        CHARACTERS, CDATA -> stack.lastOrNull()?.text?.append(r.text)
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

  // ---- parseNodes (single-pass with separate attr tracking) ----

  fun parseNodes(xml: InputStream) = parseNodes(InputSource(xml))
  fun parseNodes(xml: Reader) = parseNodes(InputSource(xml))
  fun parseNodes(xml: String) = parseNodes(StringReader(xml))

  internal fun parseNodes(xml: InputSource): XmlNode {
    val r = reader(xml)
    class Frame(val name: String, val node: MutableMap<String, Any?> = mutableMapOf(),
                val attrs: ArrayList<Pair<String, String>> = ArrayList(0),
                val text: StringBuilder = StringBuilder(), var hasChildren: Boolean = false)
    val stack = ArrayDeque<Frame>()
    while (r.hasNext()) {
      when (r.next()) {
        START_ELEMENT -> {
          val name = localName(r)
          val frame = Frame(name)
          if (r.attributeCount > 0) for (i in 0 until r.attributeCount)
            frame.attrs.add(attrName(r, i) to r.getAttributeValue(i))
          stack.lastOrNull()?.hasChildren = true
          stack.addLast(frame)
        }
        CHARACTERS, CDATA -> stack.lastOrNull()?.text?.append(r.text)
        END_ELEMENT -> {
          val frame = stack.removeLast()
          val text = frame.text.toString().trim()
          val parent = stack.lastOrNull() ?: run {
            val rootNode = frame.node
            if (text.isNotEmpty()) rootNode[""] = text
            for ((k, v) in frame.attrs) rootNode[keys.from("@$k")] = v
            if (rootNode.size > 1) rootNode.remove("")
            return mapOf(keys.from(frame.name) to if (rootNode.size == 1) rootNode[""] ?: rootNode else rootNode)
          }
          val childName = keys.from(frame.name)
          if (!frame.hasChildren && text.isNotEmpty()) {
            val existing = parent.node[childName]
            parent.node[childName] = when (existing) {
              null -> text; is MutableList<*> -> (existing as MutableList<Any?>).apply { add(text) }; else -> mutableListOf(existing, text)
            }
            for ((k, v) in frame.attrs) parent.node["$childName${keys.from("@$k")}"] = v
          } else {
            val childNode = frame.node
            if (text.isNotEmpty()) childNode[""] = text
            for ((k, v) in frame.attrs) childNode[keys.from("@$k")] = v
            val existing = parent.node[childName]
            parent.node[childName] = when (existing) {
              null -> childNode; is MutableList<*> -> (existing as MutableList<Any?>).apply { add(childNode) }; else -> mutableListOf(existing, childNode)
            }
          }
        }
      }
    }
    r.close()
    return emptyMap()
  }

  // ---- parse<T> (single-pass typed parser) ----

  inline fun <reified T: Any> parse(@Language("xml") xml: InputStream): T = parse(InputSource(xml), T::class)
  inline fun <reified T: Any> parse(@Language("xml") xml: Reader): T = parse(InputSource(xml), T::class)
  inline fun <reified T: Any> parse(@Language("xml") xml: String): T = parse(StringReader(xml))

  // Unified frame: kind 0=OBJ, 1=TXT, 2=TRK, 3=SKIP
  private class F(
    @JvmField val kind: Int,
    @JvmField val type: KClass<*>? = null,
    @JvmField val resolved: Resolved? = null,
    @JvmField val propName: String? = null,
    @JvmField val isList: Boolean = false,
    @JvmField val childMap: Map<String, List<Tracker>>? = null,
    @JvmField val textTargets: List<Tracker>? = null,
    @JvmField val targetKType: KType? = null
  ) {
    @JvmField val vals: MutableMap<String, Any?>? = if (kind == 0) HashMap(8) else null
  }

  fun <T: Any> parse(@Language("xml") xml: InputSource, type: KClass<T>): T {
    val r = reader(xml)
    val stack = ArrayList<F>(16)
    val objStack = ArrayList<F>(8)
    val textBuf = StringBuilder(256)
    var result: Any? = null
    // Text-mode: tracks leaf text children without frame allocation
    var txtDepth = 0; var txtProp = ""; var txtIsList = false

    while (r.hasNext()) {
      when (r.next()) {
        START_ELEMENT -> {
          if (txtDepth > 0) { txtDepth++; textBuf.setLength(0); continue }
          textBuf.setLength(0)
          val name = localName(r)
          if (stack.isEmpty()) {
            val res = resolve(type, name)
            val frame = F(0, type, res, childMap = res.childMap)
            readObjAttrs(r, frame)
            stack.add(frame); objStack.add(frame)
          } else {
            val top = stack[stack.size - 1]
            if (top.kind == 3) {
              stack.add(SKIP)
            } else {
              val trackers = (if (top.kind == 0) top.resolved!!.childMap else top.childMap)?.get(name)
              if (trackers == null) {
                stack.add(SKIP)
              } else if (!enterChild(r, trackers, stack, objStack)) {
                // text-mode activated: txtProp/txtIsList set by enterChild
                txtDepth = 1
              }
            }
          }
        }
        CHARACTERS, CDATA -> {
          if (txtDepth > 0) { textBuf.append(r.textCharacters, r.textStart, r.textLength); continue }
          if (stack.isEmpty()) continue
          val top = stack[stack.size - 1]
          if (top.kind != 3) textBuf.append(r.textCharacters, r.textStart, r.textLength)
        }
        END_ELEMENT -> {
          if (txtDepth > 0) { txtDepth--
            if (txtDepth == 0) { val t = trimBuf(textBuf)
              if (t.isNotEmpty()) addVal(objStack[objStack.size - 1], txtProp, txtIsList, t) }
            textBuf.setLength(0); continue }
          val frame = stack.removeAt(stack.size - 1)
          when (frame.kind) {
            0 -> {
              objStack.removeAt(objStack.size - 1)
              val text = trimBuf(textBuf)
              frame.resolved!!.textProp?.let { tp -> if (text.isNotEmpty()) frame.vals!![tp.prop.name] = text }
              val obj = buildObj(frame, text)
              val target = if (objStack.isEmpty()) null else objStack[objStack.size - 1]
              if (target == null) result = obj else addVal(target, frame.propName!!, frame.isList, obj)
            }
            2 -> {
              val tt = frame.textTargets
              if (tt != null) { val text = trimBuf(textBuf)
                if (text.isNotEmpty()) { val target = objStack[objStack.size - 1]
                  for (i in tt.indices) addVal(target, tt[i].info.prop.name, tt[i].info.isCollection, text) } }
            }
            // 3 (SKIP): nothing
          }
          textBuf.setLength(0)
        }
      }
    }
    r.close()
    return (result ?: error("XML document has no root element")) as T
  }

  private val SKIP = F(3)

  /** Returns true if a frame was pushed to stack, false if text-mode activated (caller reads txtProp/txtIsList) */
  private fun enterChild(r: XMLStreamReader, trackers: List<Tracker>, stack: ArrayList<F>, objStack: ArrayList<F>): Boolean {
    // Fast path: single tracker (very common case)
    if (trackers.size == 1) {
      val t = trackers[0]; val s = t.segs
      if (s.isEmpty()) {
        val tgt = if (t.info.isCollection) t.info.elemType else t.info.type
        if (tgt != null && isComplex(tgt)) { pushObj(r, tgt, t.info, stack, objStack); return true }
        txtProp = t.info.prop.name; txtIsList = t.info.isCollection; return false
      }
      if (s[0][0] == '@') {
        readAttr(r, s[0].substring(1), objStack[objStack.size - 1], t.info.prop.name, t.info.isCollection)
        stack.add(SKIP); return true
      }
      stack.add(F(2, childMap = mapOf(s[0] to listOf(Tracker(t.info, s.subList(1, s.size)))))); return true
    }
    // Multi-tracker
    var objTracker: Tracker? = null; var txtT: Tracker? = null; var hasCompound = false
    val target = objStack[objStack.size - 1]
    for (i in trackers.indices) { val t = trackers[i]; val s = t.segs
      when {
        s.isEmpty() -> { val tgt = if (t.info.isCollection) t.info.elemType else t.info.type
          if (tgt != null && isComplex(tgt)) objTracker = t else txtT = t }
        s[0][0] == '@' -> readAttr(r, s[0].substring(1), target, t.info.prop.name, t.info.isCollection)
        else -> hasCompound = true
      }
    }
    when {
      objTracker != null -> pushObj(r, (if (objTracker.info.isCollection) objTracker.info.elemType else objTracker.info.type)!!, objTracker.info, stack, objStack)
      hasCompound -> {
        val childMap = mutableMapOf<String, MutableList<Tracker>>()
        val textTargets = if (txtT != null) mutableListOf(txtT) else null
        for (i in trackers.indices) { val t = trackers[i]; val s = t.segs
          if (s.isNotEmpty() && s[0][0] != '@') childMap.getOrPut(s[0]) { mutableListOf() }.add(Tracker(t.info, s.subList(1, s.size))) }
        stack.add(F(2, childMap = childMap, textTargets = textTargets))
      }
      txtT != null -> { txtProp = txtT.info.prop.name; txtIsList = txtT.info.isCollection; return false }
      else -> stack.add(SKIP)
    }
    return true
  }
  // Out-params for text-mode (set by enterChild, read by caller)
  private var txtProp = ""; private var txtIsList = false

  private fun pushObj(r: XMLStreamReader, tgt: KClass<*>, info: PropInfo, stack: ArrayList<F>, objStack: ArrayList<F>) {
    val res = resolve(tgt, localName(r))
    val kType = if (info.isCollection) info.kType.arguments.firstOrNull()?.type else info.kType
    val frame = F(0, tgt, res, info.prop.name, info.isCollection, res.childMap, targetKType = kType)
    readObjAttrs(r, frame); stack.add(frame); objStack.add(frame)
  }

  private fun readObjAttrs(r: XMLStreamReader, frame: F) {
    val attrNames = frame.resolved!!.attrNames
    if (r.attributeCount == 0 || attrNames.isEmpty()) return
    for (j in attrNames.indices) { val (wanted, propInfo) = attrNames[j]
      for (i in 0 until r.attributeCount) if (attrName(r, i) == wanted) { frame.vals!![propInfo.prop.name] = r.getAttributeValue(i); break } }
  }

  private fun readAttr(r: XMLStreamReader, wanted: String, target: F, propName: String, isList: Boolean) {
    for (i in 0 until r.attributeCount) if (attrName(r, i) == wanted) { addVal(target, propName, isList, r.getAttributeValue(i)); break }
  }

  private fun addVal(target: F, propName: String, isList: Boolean, value: Any?) {
    if (isList) (target.vals!!.getOrPut(propName) { mutableListOf<Any?>() } as MutableList<Any?>).add(value)
    else target.vals!![propName] = value
  }

  private fun buildObj(frame: F, rawText: String): Any {
    if (frame.vals!!.isEmpty() && rawText.isNotEmpty() && frame.resolved!!.textProp == null) {
      frame.targetKType?.let { kType -> val c = values.from(rawText, kType); if (c !== rawText) return c ?: rawText }
      if (Converter.supports(frame.type!!)) return Converter.from(rawText, frame.targetKType ?: frame.type.starProjectedType) ?: rawText
    }
    val args = frame.vals
    for (prop in propsFor(frame.type!!)) {
      val raw = args[prop.prop.name] ?: continue
      if (prop.isCollection && raw is List<*>) {
        val elemKType = prop.kType.arguments.firstOrNull()?.type
        args[prop.prop.name] = raw.map { convertVal(it, elemKType, prop.elemType) }
      } else args[prop.prop.name] = convertVal(raw, prop.kType, prop.type)
    }
    return frame.type.createFrom(args)
  }

  private fun convertVal(raw: Any?, kType: KType?, classifier: KClass<*>?): Any? {
    if (raw == null || raw !is String) return raw
    val converted = values.from(raw, kType)
    if (converted !== raw) return converted ?: raw
    if (classifier != null && Converter.supports(classifier)) return Converter.from(raw, kType!!) ?: raw
    return raw
  }

  private fun trimBuf(buf: StringBuilder): String {
    // Avoid toString+trim when possible: check if trimming is needed
    var start = 0; var end = buf.length
    while (start < end && buf[start] <= ' ') start++
    while (end > start && buf[end - 1] <= ' ') end--
    return if (start == 0 && end == buf.length) buf.toString() else buf.substring(start, end)
  }

  // ---- shared helpers ----

  private fun reader(xml: InputSource) =
    xml.characterStream?.let { factory.createXMLStreamReader(it) } ?: factory.createXMLStreamReader(xml.byteStream)

  private fun localName(r: XMLStreamReader) = r.localName.takeIf(String::isNotEmpty) ?: r.name.toString()
  private fun attrName(r: XMLStreamReader, i: Int) = (r.getAttributeLocalName(i).takeIf(String::isNotEmpty) ?: r.getAttributeName(i).toString()).removePrefix("@")
}

typealias XmlNode = Node
