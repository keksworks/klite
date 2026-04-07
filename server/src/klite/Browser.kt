package klite

val HttpExchange.browser: String? get() = detectBrowser(header("User-Agent"))

fun detectBrowser(userAgent: String?) = userAgent?.run {
  fun String.detect(browser: String): String? {
    val p = indexOf(browser)
    return if (p >= 0) substring(lastIndexOf(' ', p) + 1, indexOf(' ', startIndex = p + 1).takeIf { it > 0 } ?: length) else null
  }

  val bot = split(' ', ';').find { it.contains("bot", ignoreCase = true) } ?: ""
  val primary = (if (contains("Mobile")) "Mobile/" else "") + (
    detect("Edg") ?: detect("Chrome") ?: detect("Firefox") ?: detect("Trident") ?: detect("MSIE") ?: detect("iOS") ?:
    if (contains("Safari")) detect("Version")?.replace("Version", "Safari")
    else detect("AppleWebKit") ?: (if (bot.isEmpty()) this else ""))?.replace(".0.0.0", "")
  listOfNotNull(bot, primary).filter { it.isNotEmpty() }.joinToString("/")
}
