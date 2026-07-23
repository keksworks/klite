package klite.json

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import klite.TSID
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.time.LocalDate
import java.util.*
import kotlin.reflect.KProperty1

class TSGeneratorTest {
  val out = ByteArrayOutputStream()
  val ts = TSGenerator(out = PrintStream(out))

  @Test fun enum() {
    expect(ts.render(SomeEnum::class)).toEqual(/* language=TypeScript */ "enum SomeEnum {HELLO = 'HELLO', WORLD = 'WORLD'}")
    expect(ts.render(SomeData.Status::class)).toEqual(/* language=TypeScript */ "enum SomeDataStatus {ACTIVE = 'ACTIVE'}")
  }

  @Test fun `enum with fields`() {
    expect(ts.render(EnumWithFields::class)).toEqual(/* language=TypeScript */ """
      enum EnumWithFields {HELLO = 'HELLO', WORLD = 'WORLD'}
      export const EnumWithFieldsData = {HELLO: {bool: true, num: 1, other: SomeEnum.HELLO}, WORLD: {bool: false, num: 2, other: SomeEnum.WORLD}}
    """.trimIndent())
  }

  @Test fun inline() {
    expect(ts.render(MyId::class)).toEqual(/* language=TypeScript */ "type MyId<T> = string")
    expect(ts.render(CountryCode::class)).toEqual(/* language=TypeScript */ "type CountryCode = string")
  }

  @Test fun `custom types with type parameter`() {
    expect(ts.render(CustomTypes::class)).toEqual(/* language=TypeScript */ "interface CustomTypes {date: LocalDate; id: TSID<CustomTypes>}")
    ts.printCustomTypes()
    expect(out.toString()).toEqual(/* language=TypeScript */ "\n" + $$"""
      // klite.TSID
      export type TSID<T> = string & {_of?: T}
      // java.time.LocalDate
      export type LocalDate = `${number}-${number}-${number}`
    """.trimIndent() + "\n")
  }

  @Test fun `interface`() {
    expect(ts.render(NoProps::class)).toEqual(null)

    expect(ts.render(Person::class)).toEqual( // language=TypeScript
      "interface Person {hello: SomeEnum; name: string}")

    expect(ts.render(SomeData::class)).toEqual( // language=TypeScript
      "interface SomeData {age: number; any: any; birthDate?: LocalDate; bytes: Array<number>; field: keyof Person; " +
        "id: MyId<SomeData>; list: Array<SomeData>; map: Partial<Record<SomeEnum, Array<SomeData>>>; name: string; " +
        "other?: SomeData; status: SomeDataStatus; tsid: TSID<SomeData>; hello: SomeEnum}")

    expect(ts.render(FieldRule::class)).toEqual( // language=TypeScript
      "interface FieldRule<T> {field: keyof Hello; limits: Record<any, number>}")
  }

  @Test fun `sealed classes`() {
    expect(ts.render(Shape::class)).toEqual(/* language=TypeScript */ """
      export type Shape = ShapeCircle | ShapeRect
      export interface ShapeCircle {radius: number}
      export interface ShapeRect {height: number; width: number}
    """.trimIndent())
  }
}

@JvmInline value class CountryCode(val value: String) {
  val isCountry get() = value.length == 2
}

@JvmInline value class MyId<out T>(val uuid: UUID = UUID.randomUUID())

enum class SomeEnum { HELLO, WORLD }
enum class EnumWithFields(val bool: Boolean, val num: Int, val other: SomeEnum) {
  HELLO(true, 1, SomeEnum.HELLO), WORLD(false, 2, SomeEnum.WORLD)
}

interface Person { val name: String; val hello get() = SomeEnum.HELLO; }

data class SomeData(override val name: String, val age: Int, val birthDate: LocalDate?, val id: MyId<SomeData>, val other: SomeData?,
                    val list: List<SomeData>, val map: Map<SomeEnum, Array<SomeData>>, val any: Any, val status: Status = Status.ACTIVE,
                    val field: KProperty1<Person, *>, val bytes: ByteArray, val tsid: TSID<SomeData>): Person {
  enum class Status { ACTIVE }
}

interface NoProps { fun onlyMethods() }

data class CustomTypes(val id: TSID<CustomTypes>, val date: LocalDate)
