package klite.annotations

import ch.tutteli.atrium.api.fluent.en_GB.messageToContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.*
import klite.*
import klite.test.CustomAnnotation
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.io.IOException
import java.io.InputStream
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.util.*
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation

class AnnotationsTest {
  val exchange = mockk<HttpExchange>()

  @Path("/context") @CustomAnnotation("class") class Routes {
    @GET fun root() = "Hello"

    @GET("/hello/:world")
    fun generic(body: String,
                @PathParam world: BigDecimal, @QueryParam date: LocalDate, @HeaderParam header: Long,
                @CookieParam cookie: Locale, @AttrParam attr: BigInteger,
                @QueryParam list: List<Int>, @QueryParam nullableList: List<String>? = null,
                @BodyParam file: FileUpload) =
      "Hello $body $world $date $header $cookie $attr $list $nullableList ${file.fileName}"

    @GET("/hello/specific") @CustomAnnotation("method") fun specific() = "Hello"

    @GET("/hello/inputstream") fun stream(body: InputStream) = "Hello"
  }

  val router = spyk(Router("", mockk(), PathParamRegexer(), emptyList(), emptyList(), emptyList()))

  @Test fun `annotated instance`() {
    router.annotated(Routes())
    verifyOrder {
      router.add(match { it.annotations.containsAll(Routes::root.annotations) })
      router.add(match { it.annotations.containsAll(Routes::stream.annotations) })
      router.add(match { it.annotations.containsAll(Routes::specific.annotations) })
      router.add(match { it.annotations.containsAll(Routes::generic.annotations) })
    }
  }

  @Test fun `method annotations win class annotations`() {
    router.annotated(Routes())
    verify {
      router.add(match { it.path.toString() == "/context" && it.findAnnotation<CustomAnnotation>()!!.hello == "class" })
      router.add(match { it.path.toString() == "/context/hello/specific" && it.findAnnotation<CustomAnnotation>()!!.hello == "method" })
    }
  }

  @Test fun `annotated class`() {
    every { router.require<Routes>() } returns Routes()
    router.annotated<Routes>()
    verify(exactly = 4) { router.add(any()) }
  }

  @Test fun `no parameter handler`() {
    val handler = FunHandler(Routes(), Routes::root)
    runBlocking { expect(handler(exchange)).toEqual("Hello") }
  }

  @Test fun `exchange parameter handler`() {
    val handler = FunHandler(Routes(), Routes::generic)
    every { exchange.body<String>() } returns "TheBody"
    every { exchange.body<FileUpload>("file") } returns FileUpload("name.txt", "text/plain", "".byteInputStream())
    every { exchange.path("world") } returns "7.9e9"
    every { exchange.query("date") } returns "2021-10-21"
    every { exchange.queryList("list") } returns listOf("5", "77")
    every { exchange.queryList("nullableList") } returns emptyList()
    every { exchange.header("header") } returns "42"
    every { exchange.cookie("cookie") } returns "et"
    every { exchange.attr<BigInteger>("attr") } returns BigInteger("90909")
    runBlocking { expect(handler(exchange)).toEqual("Hello TheBody 7.9E+9 2021-10-21 42 et 90909 [5, 77] null name.txt") }
  }

  @Test fun `nicer exception if getting parameter value fails`() {
    val handler = FunHandler(Routes(), Routes::generic)
    expect { runBlocking { handler(exchange) } }.toThrow<MockKException>().messageToContain("no answer found for HttpExchange")

    every { exchange.body<Any>(any<KType>()) } throws IOException("Kaboom!")
    expect { runBlocking { handler(exchange) } }.toThrow<IllegalArgumentException>().messageToContain("Cannot get body: Kaboom!")
  }
}
