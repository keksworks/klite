package klite.json

import klite.Converter
import klite.createFrom
import klite.publicProperties
import klite.trimToNull
import java.io.Reader
import java.text.ParseException
import kotlin.reflect.*
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.jvm.javaField

private const val EOF = '\uFFFF'

class JsonParser(private val reader: Reader, private val opts: JsonMapper) {
  private var pos: Int = 0
  private var nextChar: Char? = null

  fun readValue(type: KType?): Any? = opts.values.from(when (val c = nextNonSpace()) {
    '"' -> opts.values.from(readString().let { if (opts.trimToNull) it.trimToNull() else it }, type).let { if (it is String) type.from(it) else it }
    '{' -> readObject(type)
    '[' -> readArray(type)
    '-', '+', in '0'..'9' -> readNumber(c, type)
    't', 'f' -> readLettersOrDigits(c).toBoolean()
    'n' -> readLettersOrDigits(c).let { if (it == "null") null else fail("Unexpected $it") }
    EOF -> fail("Unexpected EOF")
    else -> fail("Unexpected char: $c")
  }, type)

  private fun readString(): String = StringBuilder().apply {
    while (true) {
      when (val c = read()) {
        '"' -> break
        '\\' -> append(readEscapedChar())
        EOF -> fail("Unfinished string, EOF")
        else -> append(c)
      }
    }
  }.toString()

  private fun readEscapedChar() = when (val c = read()) {
    'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'; 'b' -> '\b'; 'f' -> '\u000C'
    'u' -> (1..4).map { read() }.joinToString("").toInt(16).toChar()
    else -> c
  }

  private fun readNumber(c: Char, type: KType?) = readLettersOrDigits(c).let { s ->
    type?.let { opts.values.from(s, it).takeIf { it !is String } ?: it.takeIfSpecific()?.from(s) } ?:
      s.toIntOrNull() ?: s.toLongOrNull() ?: s.toDouble()
  }

  @Suppress("UNCHECKED_CAST")
  private fun readObject(type: KType?) = mutableMapOf<Any, Any?>().let { map ->
    val typeClass = type?.classifier as? KClass<*>
    val typeParams = typeClass?.typeParameters?.mapIndexed { i, t -> t.name to type.arguments[i].type }?.toMap() ?: emptyMap()
    val mapTypes = if (typeClass == Map::class) type.arguments.let { it[0].type to it[1].type } else null
    val props = if (mapTypes == null) typeClass?.publicProperties?.values?.associateBy { prop ->
      prop.jsonName ?: prop.name
    } else null

    while (true) {
      var next = nextNonSpace()
      if (next == '}') break else next.expect('"')

      val key = mapTypes?.first.from(opts.keys.from(readString()))!!
      nextNonSpace().expect(':')

      val prop = props?.get(key)?.takeIf { it.javaField != null }
      val value = readValue(substituteArguments(mapTypes?.second ?: prop?.returnType, typeParams))
      if (prop == null || prop.findAnnotation<JsonProperty>()?.readOnly != true && !prop.hasAnnotation<JsonIgnore>())
        map[prop?.name ?: key] = value

      next = nextNonSpace()
      if (next == '}') break else next.expect(',')
    }
    type?.takeIfSpecific()?.createFrom(map as Map<String, Any?>) ?: map
  }

  private fun substituteArguments(type: KType?, typeParams: Map<String, KType?>): KType? = typeParams[type.toString()] ?:
    if (typeParams.isNotEmpty() && type?.arguments?.isNotEmpty() == true) KTypeWithKnownArguments(type, typeParams) else type

  private fun readArray(type: KType?) = collectionOf(type).also { readArrayElements<Any>(type?.arguments?.firstOrNull()?.type, it::add) }

  private fun collectionOf(type: KType?): MutableCollection<Any?> = when (type?.classifier) {
    Set::class -> mutableSetOf()
    else -> mutableListOf()
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T> readArrayElements(type: KType?, consumer: (o: T) -> Unit) {
    while (true) {
      var c = nextNonSpace()
      if (c == ']') break else nextChar = c
      consumer(readValue(type) as T)
      c = nextNonSpace()
      if (c == ']') break else c.expect(',')
    }
  }

  inline fun <reified T> readArray(noinline consumer: (o: T) -> Unit) = readArray(typeOf<T>(), consumer)
  fun <T> readArray(type: KType, consumer: (o: T) -> Unit) {
    nextNonSpace().expect('[')
    readArrayElements(type, consumer)
  }

  private fun nextNonSpace(): Char {
    var char: Char
    do { char = read() } while (char.isWhitespace())
    return char
  }

  private fun readLettersOrDigits(include: Char? = null): String = StringBuilder().apply {
    if (include != null) append(include)
    while (true) {
      val c = read()
      if (c == EOF || !(c.isLetterOrDigit() || c == '.')) { if (include != null) { nextChar = c }; break }
      else append(c)
    }
  }.toString()

  private fun read(): Char = nextChar?.also { nextChar = null } ?: reader.read().toChar().also { pos++ }

  private fun fail(msg: String): Nothing = throw JsonParseException(msg, pos - 1)

  private fun Char.expect(char: Char) {
    if (this != char) fail("Expecting $char but got ${if (this == EOF) "EOF" else this}")
  }

  // TODO: puzzler: remove <Any>
  private fun KType?.from(s: String?) = if (this != null && s != null) Converter.from<Any>(s, this) else s
  private fun KType.takeIfSpecific() = takeIf { classifier != Any::class && classifier != Map::class }
}

class JsonParseException(msg: String, pos: Int): ParseException("$msg at index $pos", pos)

private class KTypeWithKnownArguments(val type: KType, val typeParams: Map<String, KType?>): KType by type {
  override val arguments: List<KTypeProjection> = type.arguments.map { a ->
    (a.type?.classifier as? KTypeParameter)?.let { typeParams[it.name] }?.let { KTypeProjection(a.variance, it) } ?: a
  }
}
