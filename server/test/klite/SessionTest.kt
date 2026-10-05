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

  @Test fun `stores session in the cookie and loads it back`() {
    val exchange = newExchange()
    val session = store.load(exchange)
    expect(session.isNew).toEqual(true)
    session["userId"] = "123"
    store.save(exchange, session)

    val loaded = store.load(newExchange(cookie = savedCookieValue!!))
    expect(loaded.isNew).toEqual(false)
    expect(loaded["userId"]).toEqual("123")
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
