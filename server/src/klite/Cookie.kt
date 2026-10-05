package klite

import java.time.Instant
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
import kotlin.time.Duration

data class Cookie(val name: String, val value: String, val expires: Instant? = null, val maxAge: Duration? = null,
                  val path: String? = "/", val domain: String? = null,
                  val httpOnly: Boolean = false, val secure: Boolean = false, val sameSite: SameSite? = null) {
  enum class SameSite { Lax, Strict, None }

  init {
    require(name.isNotEmpty() && name.none { it in ";= " }) { "Invalid cookie name" }
    path?.let { require(';' !in it) { "Invalid cookie path" } }
    domain?.let { require(';' !in it) { "Invalid cookie domain" } }
  }

  override fun toString() = "$name=${value.urlEncode()}" + (sequenceOf(
    expires?.let { "Expires=" + RFC_1123_DATE_TIME.format(it.atOffset(UTC)) },
    maxAge?.let { "Max-Age=${it.inWholeSeconds}" },
    path?.let { "Path=$it" },
    domain?.let { "Domain=$it" },
    "HttpOnly".takeIf { httpOnly }, "Secure".takeIf { secure }, sameSite?.let { "SameSite=$it" }
  ).filterNotNull().joinToString("") { "; $it" })
}

fun decodeCookies(cookies: String?): Params = cookies?.split(';')?.associate { keyValue(it.trim()) } ?: emptyMap()
