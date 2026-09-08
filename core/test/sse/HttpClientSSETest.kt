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

  @Test fun `skip empty data`() {
    expect("data:\n\ndata:ok\n\n".inputStream().parseSSE().toList()).toEqual(listOf(Event("ok")))
  }

  @Test fun `skip unknown tags`() {
    expect("retry:5000\ndata:test\n\n".inputStream().parseSSE().toList()).toEqual(listOf(Event("test")))
  }

  @Test fun `skip lines without colon`() {
    expect("comment\ndata:test\n\n".inputStream().parseSSE().toList()).toEqual(listOf(Event("test")))
  }

  @Test fun `reset id and name after each event`() {
    expect("id:1\nevent:msg\ndata:first\n\ndata:second\n\n".inputStream().parseSSE().toList()).toEqual(listOf(Event("first", "msg", "1"), Event("second")))
  }

  private fun String.inputStream() = ByteArrayInputStream(toByteArray())
}
