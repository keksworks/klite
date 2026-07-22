package klite.jdbc

import ch.tutteli.atrium.api.fluent.en_GB.toBeAnInstanceOf
import ch.tutteli.atrium.api.fluent.en_GB.toBeTheInstance
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import klite.Decimal
import klite.TSID
import klite.d
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.BigDecimal.ONE
import java.math.BigDecimal.TEN
import java.sql.Connection
import java.sql.Date
import java.sql.Timestamp
import java.time.*
import java.time.Month.DECEMBER
import java.time.Month.OCTOBER
import java.time.ZoneOffset.UTC
import java.util.*
import java.util.UUID.randomUUID
import kotlin.reflect.typeOf

class JdbcConverterTest {
  @Test fun `null`() {
    expect(JdbcConverter.to(null)).toEqual(null)
  }

  @Test fun `primitive types are supported by jdbc`() {
    expect(JdbcConverter.to(123)).toEqual(123)
    expect(JdbcConverter.to(123L)).toEqual(123L)
    expect(JdbcConverter.to(123.0)).toEqual(123.0)
  }

  @Test fun `BigInteger and BigDecimal`() {
    expect(JdbcConverter.to(123.toBigDecimal())).toEqual(123.toBigDecimal())
    expect(JdbcConverter.to(123.toBigInteger())).toEqual(123.toBigInteger())
  }

  @Test fun `to array of varchar`() {
    val conn = mockk<Connection>(relaxed = true)
    expect(JdbcConverter.to(listOf("hello"), conn)).toBeAnInstanceOf<java.sql.Array>()
    verify { conn.createArrayOf("varchar", arrayOf("hello")) }

    expect(JdbcConverter.to(setOf("set"), conn)).toBeAnInstanceOf<java.sql.Array>()
    verify { conn.createArrayOf("varchar", arrayOf("set")) }

    expect(JdbcConverter.to(arrayOf("a1", "a2"), conn)).toBeAnInstanceOf<java.sql.Array>()
    verify { conn.createArrayOf("varchar", arrayOf("a1", "a2")) }
  }

  @Test fun `to array of uuid`() {
    val conn = mockk<Connection>(relaxed = true)
    val uuid = randomUUID()
    expect(JdbcConverter.to(listOf(uuid, uuid), conn)).toBeAnInstanceOf<java.sql.Array>()
    verify { conn.createArrayOf("uuid", arrayOf(uuid, uuid)) }
  }

  @Test fun `to array of BigDecimal`() {
    val conn = mockk<Connection>(relaxed = true)
    expect(JdbcConverter.to(listOf(ONE, TEN), conn)).toBeAnInstanceOf<java.sql.Array>()
    verify { conn.createArrayOf("numeric", arrayOf(ONE, TEN)) }
  }

  @Test fun `from array of BigDecimal`() {
    val array = mockk<java.sql.Array>(relaxed = true) {
      every { array } returns arrayOf(ONE, TEN)
    }
    expect(JdbcConverter.from(array, typeOf<Array<BigDecimal>>())).toEqual(array.array)
    expect(JdbcConverter.from(array, typeOf<Set<BigDecimal>>())).toEqual(setOf(ONE, TEN))
    expect(JdbcConverter.from(array, typeOf<List<Decimal>>())).toEqual(listOf(1.d, 10.d))
  }

  @Test fun `to array of convertible types`() {
    val conn = mockk<Connection>(relaxed = true)
    expect(JdbcConverter.to(listOf(OCTOBER, Year.of(2024)), conn)).toBeAnInstanceOf<java.sql.Array>()
    verify { conn.createArrayOf("varchar", arrayOf(OCTOBER.toString(), "2024")) }
  }

  @Test fun `from array of convertible types`() {
    val array = mockk<java.sql.Array>(relaxed = true) {
      every { array } returns arrayOf(OCTOBER.toString(), DECEMBER.toString())
    }
    expect(JdbcConverter.from(array, typeOf<Set<Month>>())).toEqual(setOf(OCTOBER, DECEMBER))
    expect(JdbcConverter.from(array, typeOf<List<Month>>())).toEqual(listOf(OCTOBER, DECEMBER))
  }

  @Test fun `to array of inline TSID`() {
    val conn = mockk<Connection>(relaxed = true)
    expect(JdbcConverter.to(listOf(TSID<Any>(12345)), conn)).toBeAnInstanceOf<java.sql.Array>()
    verify { conn.createArrayOf("numeric", arrayOf(12345L)) }
  }

  @Test fun `from array of inline TSID`() {
    val array = mockk<java.sql.Array>(relaxed = true) {
      every { array } returns arrayOf(12345L)
    }
    expect(JdbcConverter.from(array, typeOf<List<TSID<Any>>>())).toEqual(listOf(TSID<Any>(12345L)))
  }

  @Test fun `to local date and time`() {
    expect(JdbcConverter.to(LocalDate.of(2021, 10, 21))).toEqual(LocalDate.of(2021, 10, 21))
    expect(JdbcConverter.to(LocalDateTime.MIN)).toBeTheInstance(LocalDateTime.MIN)
    expect(JdbcConverter.to(LocalTime.MIN)).toBeTheInstance(LocalTime.MIN)
    expect(JdbcConverter.to(OffsetDateTime.MIN)).toBeTheInstance(OffsetDateTime.MIN)
  }

  @Test fun `from local date and time`() {
    expect(JdbcConverter.from(Date(123), LocalDate::class)).toEqual(LocalDate.of(1970, 1, 1))
    expect(JdbcConverter.from(Timestamp(123), Instant::class)).toEqual(Instant.ofEpochMilli(123))
    expect(JdbcConverter.from(Timestamp(123), LocalDateTime::class)).toEqual(Timestamp(123).toLocalDateTime())
    expect(JdbcConverter.from(null, LocalDateTime::class)).toEqual(null)
    expect(JdbcConverter.from(null, Instant::class)).toEqual(null)
  }

  @Test fun `Instant should be converted to offset`() {
    expect(JdbcConverter.to(Instant.EPOCH)).toEqual(Instant.EPOCH.atOffset(UTC))
  }

  @Test fun `toString types`() {
    expect(JdbcConverter.to(Currency.getInstance("EUR"))).toEqual("EUR")
    expect(JdbcConverter.to(Locale.of("et"))).toEqual("et")
    expect(JdbcConverter.to(Period.ofDays(3))).toEqual("P3D")
  }

  @Test fun `from toString types`() {
    expect(JdbcConverter.from("EUR", Currency::class)).toEqual(Currency.getInstance("EUR"))
    expect(JdbcConverter.from("et", Locale::class)).toEqual(Locale.of("et"))
    expect(JdbcConverter.from("P3D", Period::class)).toEqual(Period.ofDays(3))
    expect(JdbcConverter.from("13.456".toBigDecimal(), Decimal::class)).toEqual(13.46.d)
  }

  @Test fun `inline class`() {
    val email = Email("hello@example.com")
    expect(JdbcConverter.to(email)).toEqual(email.email)
    expect(JdbcConverter.from(email.email, typeOf<Email>())).toEqual(email)
  }

  @JvmInline value class Email(val email: String)
}
