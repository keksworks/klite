package klite.html

import org.intellij.lang.annotations.Language

// Simple helpers for outputting server-side HTML as Kotlin template strings

@Language("html")
fun <T> Iterable<T>.each(transform: (IndexedValue<T>) -> String) = withIndex().joinToString(separator = "", transform = transform)

fun String.escapeHtml() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
fun String.escapeJs() = replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"").replace("<", "\\u003c")

/** Usage: """${+dataToBeEscaped}""" */
operator fun Any?.unaryPlus() = this?.toString()?.escapeHtml() ?: ""
