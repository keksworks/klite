package klite.sse

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class HttpClientSSETest {
  @Test fun `parse data only`() {
    expect("data: hello\n\n".inputStream().parseSSE().toList()).toEqual(listOf(Event("hello")))
  }

  @Test fun `parse data with event name and id`() {
    expect("id:1\nevent:msg\ndata:hello\n\n".inputStream().parseSSE().toList()).toEqual(listOf(Event("hello", "msg", "1")))
  }

  @Test fun `parse multiple events`() {
    expect("data:first\n\nevent:custom\ndata:second\n\n".inputStream().parseSSE().toList()).toEqual(listOf(Event("first"), Event("second", "custom")))
  }

  @Test fun `skip unknown tags`() {
    expect("retry:5000\ndata:test\n\n".inputStream().parseSSE().toList()).toEqual(listOf(Event("test")))
  }

  @Test fun `multiline data`() {
    expect("comment\nevent:hello\ndata:line1\ndata:line2\n\n".inputStream().parseSSE().toList()).toEqual(listOf(Event("line1\nline2", name = "hello")))
  }

  @Test fun `reset id and name after each event`() {
    expect("id:1\nevent:msg\ndata:first\n\ndata:second\n\n".inputStream().parseSSE().toList()).toEqual(listOf(Event("first", "msg", "1"), Event("second")))
  }

  private fun String.inputStream() = ByteArrayInputStream(toByteArray())
}
