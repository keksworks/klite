package klite

import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import com.sun.net.httpserver.Headers
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class SessionTest {
  abstract class MockableExchange: OriginalHttpExchange()

  val store = CookieSessionStore("session-secret")
  val requestHeaders = Headers()
  val responseHeaders = Headers()

  @Test fun `holds non-string values`() {
    val tsid = TSID<Any>(11_111_101_234_567_890L)
    val session = Session()
    session["s"] = "text"
    session["i"] = 42
    session["l"] = 11_111_101_234_567_890L
    session["t"] = tsid

    expect(session.get<String>("s")).toEqual("text")
    expect(session.get<Int>("i")).toEqual(42)
    expect(session.get<Long>("l")).toEqual(11_111_101_234_567_890L)
    expect(session.get<TSID<Any>>("t")).toEqual(tsid)
    // the plain getter stringifies, which for a TSID means its base36 toString
    expect(session["i"]).toEqual("42")
    expect(session["t"]).toEqual(tsid.toString())

    // a value stored as a plain number is wrapped into the inline class when that is what is requested
    session["n"] = 1234L
    expect(session.get<TSID<Any>>("n")).toEqual(TSID<Any>(1234L))
    expect(session.get<Long>("n")).toEqual(1234L)
    // a small number can also arrive as an Int, e.g. from a JSON parser, and is still wrapped
    session["small"] = 1234
    expect(session.get<TSID<Any>>("small")).toEqual(TSID<Any>(1234L))
  }

  @Test fun `stores session in the cookie and loads it back`() {
    val tsid = TSID<Any>(11_111_101_234_567_890L)
    val exchange = newExchange()
    val session = store.load(exchange)
    expect(session.isNew).toEqual(true)
    session["userId"] = "123"
    session["tsid"] = tsid
    store.save(exchange, session)

    val loaded = store.load(newExchange(cookie = savedCookieValue!!))
    expect(loaded.isNew).toEqual(false)
    expect(loaded["userId"]).toEqual("123")
    // a cookie stores a TSID as its base36 toString, which is converted back
    expect(loaded.get<TSID<Any>>("tsid")).toEqual(tsid)
  }

  @Test fun `does not touch the cookie when session is unchanged`() {
    store.save(newExchange(), store.load(newExchange()))
    expect(responseHeaders["Set-Cookie"]).toEqual(null)
  }

  @Test fun `clear expires the cookie`() {
    val session = store.load(newExchange(cookie = validCookie()))
    session.clear()
    store.save(newExchange(), session)

    val setCookie = savedCookie!!
    expect(setCookie).toContain("S=")
    expect(setCookie).toContain("Max-Age=0")
  }

  @Test fun `clearing and then writing keeps a valid cookie`() {
    val session = store.load(newExchange(cookie = validCookie()))
    session.clear()
    session["userId"] = "456"
    store.save(newExchange(), session)

    expect(savedCookie!!.contains("Max-Age=0")).toEqual(false)
    expect(store.load(newExchange(cookie = savedCookieValue!!))["userId"]).toEqual("456")
  }

  /** an existing cookie of the configured store, to clear a real session */
  private fun validCookie(): String {
    val exchange = newExchange()
    val session = store.load(exchange)
    session["userId"] = "123"
    store.save(exchange, session)
    return savedCookieValue!!
  }

  private val savedCookie get() = responseHeaders["Set-Cookie"]?.single()
  private val savedCookieValue get() = savedCookie?.substringAfter("S=")?.substringBefore(';')

  private fun newExchange(cookie: String? = null): HttpExchange {
    requestHeaders.clear()
    responseHeaders.clear()
    cookie?.let { requestHeaders.add("Cookie", "S=$it") }
    val original = mockk<MockableExchange>(relaxed = true) {
      every { requestMethod } returns "GET"
      every { responseCode } returns -1
      every { requestHeaders } returns this@SessionTest.requestHeaders
      every { responseHeaders } returns this@SessionTest.responseHeaders
    }
    return HttpExchange(original, mockk<RouterConfig>(relaxed = true), store, "req-id")
  }
}
