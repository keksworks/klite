package klite

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.toBeTheInstance
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.LocalDate
import java.time.Period
import java.util.*
import java.util.UUID.fromString
import java.util.UUID.randomUUID
import java.util.regex.Pattern
import kotlin.reflect.KProperty1
import kotlin.reflect.typeOf

class ConverterTest {
  @Test fun `pre-defined`() {
    expect(Converter.from<Locale>("et_EE")).toEqual(Locale.of("et", "EE"))
    expect(Converter.from<Decimal>("123.45")).toEqual("123.45".d)
    expect(Converter.supports(Locale::class)).toEqual(true)
  }

  @Test fun `static method`() {
    expect(Converter.from<UUID>("05e1cebe-67dc-4780-b5df-472edd55fab6")).toEqual("05e1cebe-67dc-4780-b5df-472edd55fab6".uuid)
    expect(Converter.from<Currency>("EUR")).toEqual(Currency.getInstance("EUR"))
    expect(Converter.from<LocalDate>("2021-10-21")).toEqual(LocalDate.parse("2021-10-21"))
    expect(Converter.from<Period>("P1D")).toEqual(Period.parse("P1D"))
    expect(Converter.from<Pattern>("[a-z]").pattern()).toEqual("[a-z]")
    expect { Converter.from<List<Any>>("s") }.toThrow<UnsupportedOperationException>()
  }

  @Test fun custom() {
    expect(Converter.supports(Converter::class)).toEqual(false)
    Converter.use { Converter }
    expect(Converter.supports(Converter::class)).toEqual(true)
    expect(Converter.from<Converter>("any")).toBeTheInstance(Converter)
  }

  @Test fun `super classes`() {
    expect(Converter.from<Any>("x")).toEqual("x")
    expect(Converter.from<CharSequence>("x")).toEqual("x")
  }

  @Test fun enum() {
    expect(Converter.from<AnnotationTarget>("FIELD")).toEqual(AnnotationTarget.FIELD)
    expect(Converter.from<AnnotationTarget>("field")).toEqual(AnnotationTarget.FIELD)
  }

  @Test fun constructor() {
    expect(Converter.from<URI>("http://hello/")).toEqual(URI("http://hello/"))
    expect(Converter.from<Int>("123")).toEqual(123)
    expect(Converter.from<Long>("123")).toEqual(123L)
    expect(Converter.from<Email>("tere@tere.ee")).toEqual(Email("tere@tere.ee"))
    expect(Converter.from<Phone>("+3726123456")).toEqual(Phone("+3726123456"))
  }

  @Test fun `no-auto creation of data`() {
    expect(Converter.supports(SingleValueData::class)).toEqual(false)
    expect { Converter.from<SingleValueData>("hello") }.toThrow<UnsupportedOperationException>().messageToContain("No known converter from String to class klite.SingleValueData, register with Converter.use()")
    Converter.use { SingleValueData(it) }
    expect(Converter.from<SingleValueData>("hello")).toEqual(SingleValueData("hello"))
  }

  @Test fun `companion object initialization`() {
    expect(Converter.from<DataWithCompanion>("hello")).toEqual(DataWithCompanion("hello"))
    expect(Converter.from<KProperty1<IndirectDataWithCompanion, Any>>("s")).toEqual(IndirectDataWithCompanion::s)
  }

  @Test fun `backwards-compatibility for not exact types deserialized from json`() {
    expect(Converter.from("s", NotExactField<*>::f.returnType) as Any).toEqual("s")
  }

  @Test fun jvmInline() {
    expect(Converter.from<Inline>("hello")).toEqual(Inline("hello"))
    val id = randomUUID()
    expect(Converter.from<InlineId>(id.toString())).toEqual(InlineId(id))
  }

  @Test fun javaPrimitive() {
    expect(Converter.from("456", Int::class)).toEqual(456)
  }

  @Test fun <T> `non-class type`() {
    val type = typeOf<List<T>>()
    expect(Converter.from<Any>("s", type.arguments.first().type!!)).toEqual("s")
  }

  @Test fun `no creator`() {
    expect { Converter.from<ConverterTest>("some string") }.toThrow<UnsupportedOperationException>()
  }
}

@JvmInline value class Inline(val string: String)
@JvmInline value class InlineId(val id: UUID) {
  constructor(s: String): this(fromString(s))
}

data class SingleValueData(val s: String)

data class DataWithCompanion(val s: String) {
  companion object {
    init { Converter.use { DataWithCompanion(it) } }
  }
}

data class IndirectDataWithCompanion(val s: String) {
  companion object {
    init { Converter.use { IndirectDataWithCompanion::class.publicProperties[it]!! } }
  }
}

class NotExactField<T: Any>(val f: Comparable<T>)
