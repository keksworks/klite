package klite.xml

import kotlin.text.RegexOption.DOT_MATCHES_ALL

fun String.dropXmlHeader() = substringAfter("?>").trim()
fun String.dropXmlRoot() = dropXmlHeader().substringAfter(">").substringBeforeLast("<").trim()

private val nsRegex = "(</?)([^:>\\s]+):".toRegex()

fun String.extractXmlTag(tagName: String, preserveNs: Set<String> = emptySet()): String {
  val nsPrefixes = preserveNs.associateBy { """xmlns:([^=]+?)="$it"""".toRegex().find(this)?.groups?.get(1)?.value }
  val tagRegex = "<([^:>]+:|)$tagName(?:\\s[^>]*)?>.*?</([^:>]+:|)$tagName>".toRegex(DOT_MATCHES_ALL)
  val inner = tagRegex.find(this)?.value!!
  val stripped = nsRegex.replace(inner) {
    val prefix = it.groups[2]!!.value
    if (prefix in nsPrefixes) it.value else it.groups[1]!!.value
  }
  val i = stripped.indexOf('>')
  return stripped.substring(0, i) +
    (if (nsPrefixes.isEmpty()) "" else " ") +
    nsPrefixes.entries.joinToString(separator = " ") { (prefix, uri) -> """xmlns:$prefix="$uri"""" } +
    stripped.substring(i)
}
