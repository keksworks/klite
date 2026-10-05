package klite.sample.klite.jdbc

import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import com.sun.net.httpserver.Headers
import io.mockk.every
import io.mockk.mockk
import klite.HttpExchange
import klite.OriginalHttpExchange
import klite.RouterConfig
import klite.TSID
import klite.jdbc.*
import klite.sample.DBTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*
import java.util.UUID.randomUUID

class DBSessionStoreTest: DBTest() {
  abstract class MockableExchange: OriginalHttpExchange()

  val store = DBSessionStore(db)
  val requestHeaders = Headers()
  val responseHeaders = Headers()

  /** Runs the changeset the store is documented to ship, so that its SQL is verified as well */
  @BeforeEach fun createTable() = ChangeSetFileReader("migrator/db_sessions.sql").forEach { changeSet ->
    changeSet.statements.forEach { db.exec(it) }
  }

  @Test fun `saves session and loads it back by the cookie id`() {
    val exchange = newExchange()
    val session = store.load(exchange)
    expect(session.isNew).toEqual(true)
    session["userId"] = "123"
    store.save(exchange, session)

    val id = savedId!!
    expect(runCatching { UUID.fromString(id) }.isSuccess).toEqual(true)
    val loaded = store.load(newExchange(cookie = id))
    expect(loaded.isNew).toEqual(false)
    expect(loaded["userId"]).toEqual("123")
  }

  @Test fun `updates the same row on subsequent saves`() {
    val first = newExchange()
    val session = store.load(first)
    session["a"] = "1"
    store.save(first, session)
    val id = savedId

    val second = newExchange(cookie = id)
    val loaded = store.load(second)
    loaded["b"] = "2"
    store.save(second, loaded)

    expect(db.count("db_sessions")).toEqual(1L)
    val reloaded = store.load(newExchange(cookie = id))
    expect(reloaded["a"]).toEqual("1")
    expect(reloaded["b"]).toEqual("2")
  }


  @Test fun `keeps non-string attributes`() {
    val exchange = newExchange()
    val session = store.load(exchange)
    session["s"] = "text"
    session["i"] = 42
    session["l"] = 11_111_101_234_567_890L
    session["t"] = TSID<Any>(11_111_101_234_567_890L)
    store.save(exchange, session)

    val loaded = store.load(newExchange(cookie = savedId!!))
    // jsonb values come back as their text form, just like CookieSessionStore stores them, so get() converts them
    expect(loaded.params["s"]).toEqual("text")
    expect(loaded.params["i"]).toEqual("42")
    expect(loaded.params["l"]).toEqual("11111101234567890")
    expect(loaded.get<String>("s")).toEqual("text")
    expect(loaded.get<Int>("i")).toEqual(42)
    expect(loaded.get<Long>("l")).toEqual(11_111_101_234_567_890L)
    // a TSID is written as a jsonb number, so its value survives exactly, but it is not read back as a TSID
    expect(loaded.params["t"]).toEqual("11111101234567890")
    expect(loaded.get<Long>("t")).toEqual(11_111_101_234_567_890L)
  }

  @Test fun `does not store an unchanged session`() {
    store.save(newExchange(), store.load(newExchange()))
    expect(db.count("db_sessions")).toEqual(0L)
  }

  @Test fun `ignores an id that is not in the table`() {
    val chosen = randomUUID()
    val exchange = newExchange(cookie = chosen.toString())
    val session = store.load(exchange)
    expect(session.isNew).toEqual(true)
    expect(session.params).toBeEmpty()

    session["userId"] = "not-yours"
    store.save(exchange, session)
    // the id chosen by the client must never become the stored session id, otherwise it could be fixed
    expect(db.query("select id from db_sessions") { getUuid() }.single() != chosen).toEqual(true)
  }

  @Test fun `ignores a malformed cookie value`() {
    expect(store.load(newExchange(cookie = "not-a-uuid")).isNew).toEqual(true)
  }

  @Test fun `clear deletes the stored session`() {
    val first = newExchange()
    val session = store.load(first)
    session["userId"] = "1"
    store.save(first, session)
    val id = savedId!!

    val second = newExchange(cookie = id)
    val loaded = store.load(second)
    loaded.clear()
    store.save(second, loaded)

    expect(db.count("db_sessions")).toEqual(0L)
    expect(store.load(newExchange(cookie = id)).isNew).toEqual(true)
  }

  @Test fun `clearing then writing issues a new session id`() {
    val first = newExchange()
    val session = store.load(first)
    session["userId"] = "1"
    store.save(first, session)
    val oldId = savedId!!

    // logging in clears the session before setting the authenticated attributes, the old id must not be kept
    val second = newExchange(cookie = oldId)
    val loaded = store.load(second)
    loaded.clear()
    loaded["userId"] = "2"
    store.save(second, loaded)

    val newId = savedId!!
    expect(newId != oldId).toEqual(true)
    expect(db.count("db_sessions")).toEqual(1L)
    expect(store.load(newExchange(cookie = oldId)).isNew).toEqual(true)
    expect(store.load(newExchange(cookie = newId))["userId"]).toEqual("2")
  }

  private val savedId get() = responseHeaders["Set-Cookie"]?.single()?.substringAfter("S=")?.substringBefore(';')

  private fun newExchange(cookie: String? = null): HttpExchange {
    requestHeaders.clear()
    responseHeaders.clear()
    cookie?.let { requestHeaders.add("Cookie", "S=$it") }
    val original = mockk<MockableExchange>(relaxed = true) {
      every { requestMethod } returns "GET"
      every { responseCode } returns -1
      every { requestHeaders } returns this@DBSessionStoreTest.requestHeaders
      every { responseHeaders } returns this@DBSessionStoreTest.responseHeaders
    }
    return HttpExchange(original, mockk<RouterConfig>(relaxed = true), store, "req-id")
  }
}
