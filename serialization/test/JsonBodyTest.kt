@file:UseSerializers(UUIDSerializer::class)
package klite.serialization

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import klite.ErrorResponse
import klite.StatusCode
import klite.uuid
import kotlinx.datetime.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.reflect.typeOf

@ExperimentalSerializationApi
class JsonBodyTest {
  val jsonBody = JsonBody()

  @Test fun `can create data classes`() {
    val someData: SomeData = jsonBody.parse("""{"email":"a@b.ee","date":"2021-12-12","extra":123}""".byteInputStream(), typeOf<SomeData>())
    expect(someData).toEqual(SomeData(Email("a@b.ee"), LocalDate.parse("2021-12-12")))
  }

  @Test fun `can serialize ErrorResponse`() {
    val out = ByteArrayOutputStream()
    jsonBody.render(out, ErrorResponse(StatusCode.NotFound, "/"))
    expect(out.toByteArray().decodeToString()).toEqual("""{"type":null,"title":"Not Found","status":404,"detail":"/"}""")
  }

  @Test fun `use ConverterSerializer`() {
    val out = ByteArrayOutputStream()
    val entity = SomeEntity("fc587008-f555-4b4d-82c0-818b05eb8bad".uuid)
    jsonBody.render(out, entity)
    expect(out.toByteArray().decodeToString()).toEqual("""{"id":"fc587008-f555-4b4d-82c0-818b05eb8bad"}""")

    expect(jsonBody.parse<SomeEntity>("""{"id":"fc587008-f555-4b4d-82c0-818b05eb8bad"}""".byteInputStream(), typeOf<SomeEntity>())).toEqual(entity)
  }
}

@Serializable @JvmInline value class Email(val email: String)
@Serializable data class SomeData(val email: Email, val date: LocalDate, val optional: String? = null)

@Serializable data class SomeEntity(val id: UUID)
