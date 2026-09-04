package klite.xml

import org.intellij.lang.annotations.Language
import kotlin.text.RegexOption.DOT_MATCHES_ALL

@Language("xml") fun String.dropXmlHeader() = substringAfter("?>").trim()
@Language("xml") fun String.dropXmlRoot() = dropXmlHeader().substringAfter(">").substringBeforeLast("<").trim()

private val nsRegex = "(</?)([^:>\\s]+):".toRegex()

@Language("xml")
fun String.extractXmlTag(tagName: String, preserveNs: Set<String> = emptySet()): String {
  val nsPrefixes = preserveNs.associateBy { """xmlns:([^=]+?)="$it"""".toRegex().find(this)?.groups?.get(1)?.value }
  val tagRegex = "<([^:>]+:|)$tagName(?:\\s[^>]*)?>.*?</([^:>]+:|)$tagName>".toRegex(DOT_MATCHES_ALL)
  val inner = tagRegex.find(this)?.value!!
  val stripped = nsRegex.replace(inner) {
    val prefix = it.groups[2]!!.value
    if (prefix in nsPrefixes) it.value else it.groups[1]!!.value
  }
  val i = stripped.indexOf('>')
  val openingTag = stripped.substring(0, i)
  val nsToAdd = nsPrefixes.entries.filter { (_, uri) -> uri !in openingTag }.map { (prefix, uri) -> """xmlns:$prefix="$uri"""" }
  return openingTag + (if (nsToAdd.isEmpty()) "" else " ") + nsToAdd.joinToString(" ") + stripped.substring(i)
}
