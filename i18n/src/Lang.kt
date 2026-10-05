package klite.i18n

import klite.HttpExchange
import klite.json.JsonMapper
import klite.json.parse
import klite.logger
import klite.warn

typealias Translations = Map<String, Any>
private typealias MutableTranslations = MutableMap<String, Any>

object Lang {
  const val COOKIE = "LANG"
  private val jsonMapper = JsonMapper(trimToNull = false)
  var jsonFiles: (lang: String) -> List<String> = { lang -> ["$lang.json"] }

  val available: List<String> = load("langs.json")
  private val translations by lazy { loadTranslations() }
  internal val log = logger()

  fun takeIfAvailable(lang: String?) = lang?.takeIf { available.contains(it) }
  fun ensureAvailable(requestedLang: String?) = takeIfAvailable(requestedLang) ?: available.first()

  fun translations(requestedLang: String?): Translations = translations[ensureAvailable(requestedLang)]!!

  fun translateOrNull(lang: String, key: String, substitutions: Map<String, String>? = null) =
    (translations(lang).resolve(key) as? String)?.substitute(substitutions)

  fun translate(lang: String, key: String, substitutions: Map<String, String> = emptyMap()) =
    translations(lang).invoke(key, substitutions)

  private fun loadTranslations(): Map<String, Translations> {
    val loaded = available.associateWith { lang -> mutableMapOf<String, Any>().also {
      jsonFiles(lang).forEach { file -> merge(it, load<MutableTranslations>(file)) }
    }}
    val default = loaded[available[0]]!!
    available.drop(1).forEach { lang -> merge(loaded[lang] as MutableTranslations, default) }
    return loaded
  }

  @Suppress("UNCHECKED_CAST")
  private fun merge(dest: MutableTranslations, src: Translations) {
    src.forEach { (key, value) ->
      if (value is Map<*, *>) {
        if (dest[key] == null) dest[key] = mutableMapOf<String, Any>()
        merge(dest[key] as MutableTranslations, value as Translations)
      }
      else if (dest[key] == null) dest[key] = value
    }
  }

  private inline fun <reified T: Any> load(filePath: String): T = jsonMapper.parse(
    javaClass.getResourceAsStream("/$filePath") ?: error("$filePath not found in classpath"))
}

private fun Translations.resolve(key: String, substitutions: Map<String, String>? = null) =
  key.split('.').fold(this) { more: Any?, k -> (more as? Map<*, *>)?.get(k) }

@Suppress("UNCHECKED_CAST")
fun Translations.getMany(key: String) = resolve(key) as? Map<String, String> ?: emptyMap()
operator fun Translations.invoke(key: String, substitutions: Map<String, String>? = null): String {
  val result = resolve(key) as? String? ?: return key.also { Lang.log.warn("Missing translation for '$key'") }
  return result.substitute(substitutions)
}

private fun String.substitute(substitutions: Map<String, String>?) =
  substitutions?.entries?.fold(this) { str, (key, value) -> str.replace("{$key}", value) } ?: this

var HttpExchange.lang: String
  get() = Lang.ensureAvailable(cookie(Lang.COOKIE))
  set(value) = cookie(Lang.COOKIE, value)

fun HttpExchange.translate(key: String, substitutions: Map<String, String> = emptyMap()) =
  Lang.translate(lang, key, substitutions)
