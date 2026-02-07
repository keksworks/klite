package klite.i18n

import klite.Config
import klite.HttpExchange
import klite.json.JsonMapper
import klite.json.parse

typealias Translations = Map<String, Any>
private typealias MutableTranslations = MutableMap<String, Any>

object Lang {
  const val COOKIE = "LANG"
  val jsonMapper = JsonMapper(trimToNull = false)
  private val suffixes: List<String> = Config.optional("LANG_LOAD_SUFFIXES", "").split(',').filter { it.isNotEmpty() }

  val available: List<String> = load("langs")
  private val translations = loadTranslations()

  fun takeIfAvailable(lang: String?) = lang?.takeIf { available.contains(it) }
  fun ensureAvailable(requestedLang: String?) = takeIfAvailable(requestedLang) ?: available.first()

  fun translations(requestedLang: String?): Translations = translations[ensureAvailable(requestedLang)]!!

  fun translate(lang: String, key: String, substitutions: Map<String, String> = emptyMap()) =
    translations(lang).invoke(key, substitutions)

  private fun loadTranslations(): Map<String, Translations> {
    val loaded = available.associateWith { lang ->
      load<MutableTranslations>(lang).also {
        suffixes.forEach { suffix -> merge(it, load<MutableTranslations>("$lang-$suffix")) }
      }
    }
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

  private inline fun <reified T: Any> load(lang: String): T = jsonMapper.parse(
    javaClass.getResourceAsStream("/$lang.json") ?: error("/$lang.json not found in classpath"))
}

private fun Translations.resolve(key: String) =
  key.split('.').fold(this) { more: Any?, k -> (more as? Map<*, *>)?.get(k) }

@Suppress("UNCHECKED_CAST")
fun Translations.getMany(key: String) = resolve(key) as? Map<String, String> ?: emptyMap()
operator fun Translations.invoke(key: String) = resolve(key) as? String ?: key
operator fun Translations.invoke(key: String, substitutions: Map<String, String> = emptyMap()): String {
  var result = invoke(key)
  substitutions.forEach { result = result.replace("{${it.key}}", it.value) }
  return result
}

var HttpExchange.lang: String
  get() = Lang.ensureAvailable(cookie(Lang.COOKIE))
  set(value) = cookie(Lang.COOKIE, value)

fun HttpExchange.translate(key: String, substitutions: Map<String, String> = emptyMap()) =
  Lang.translate(lang, key, substitutions)
