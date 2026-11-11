package klite.email

import klite.MimeTypes
import klite.html.escapeHtml
import klite.html.unaryPlus
import klite.i18n.Lang.translate
import klite.i18n.Lang.translateOrNull
import org.intellij.lang.annotations.Language
import java.net.URI
import java.util.*

open class EmailContent(val lang: String, val labelKey: String, val substitutions: Map<String, String> = emptyMap(),
                        val actionUrl: URI? = null, val headers: Map<String, String> = emptyMap()) {
  companion object {
    var logoUrl: URI? = null
    var bgCssColor = "rgb(243, 244, 246)"
    var primaryCssColor = "rgb(17, 24, 39)"
  }

  open val subject get() = translate(lang, "emails.$labelKey.subject", substitutions)
  open val body get() = translateOrNull(lang, "emails.$labelKey.body", substitutions)
  open val bodyHtml get() = translateOrNull(lang, "emails.$labelKey.bodyHtml", htmlSubstitutions)
  open val actionLabel get() = translateOrNull(lang, "emails.$labelKey.action", substitutions)
  open val from: Named? get() = null

  protected open val htmlSubstitutions get() = substitutions.mapValues { it.value.escapeHtml() }

  override fun equals(other: Any?) = other is EmailContent && javaClass == other.javaClass && lang == other.lang && labelKey == other.labelKey && substitutions == other.substitutions && actionUrl == other.actionUrl && headers == other.headers
  override fun hashCode() = Objects.hash(lang, labelKey, substitutions, actionUrl, headers)
  override fun toString() = "${this::class.simpleName}($lang, $labelKey, $substitutions, $actionUrl, $headers)"

  @Language("html")
  open fun fullHtml() = """
<html lang="$lang">
<head>
  <meta charset="${MimeTypes.textCharset}">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <meta name="x-apple-disable-message-reformatting">
  <style>
    body {
      font-family: sans-serif;
      line-height: 1.375em;
      margin: 0;
      padding: 0;
      word-spacing: normal;
      text-align: left;
      text-size-adjust: 100%;
    }

    table {
      border: none;
      border-spacing: 0;
    }
  </style>
</head>
<body>
${contentHtml()}
</body>
</html>
"""

  @Language("html")
  protected open fun contentHtml() = translate(lang, "title").let { title -> """
<div role="article" aria-roledescription="email" lang="$lang" style="background-color: $bgCssColor; padding: 1em">
  <table role="presentation" style="width: 94%; max-width: 480px; margin: 0 auto">
    <tr>
      <td style="padding: 2em; background: white; color: $primaryCssColor">
        ${logoUrl?.let {"""
          <div><img height="32" alt="${+title}" src="$it"></div>
        """} ?: ""}
        <h1 style="margin: 1em 0; font-size: 1.625em; line-height: 1.25; font-weight: bold">${+subject}</h1>
        <div style="margin-bottom: 1em; white-space: pre-line">${bodyHtml ?: +body}</div>
        ${actionUrl?.let {"""
          <a href="$it" style="background: $primaryCssColor; font-weight: bold; text-decoration: none; text-align: center; padding: 1em 2em; color: white; border-radius: 4px; margin-bottom: 1em; display: block">
            ${+(actionLabel ?: title)}
          </a>
        """} ?: ""}
        <p style="margin: 0; font-size: 0.8em; color: rgb(107, 114, 128); text-align: center">
          ${+title}
        </p>
      </td>
    </tr>
  </table>
</div>
""" }
}
