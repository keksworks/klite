package klite.sse

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import klite.TextBodyRenderer
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class ParseSSETest {
  val out = ByteArrayOutputStream()

  @Test fun `multiline string message`() {
    Event("data1\ndata2", "my-event", "idx").sendTo(out)
    expect(out.toString()).toEqual("id: idx\nevent: my-event\ndata: data1\ndata: data2\n\n")
  }

  @Test fun `only data`() {
    Event("data1").sendTo(out)
    expect(out.toString()).toEqual("data: data1\n\n")
  }

  @Test fun `data with renderer`() {
    Event("as\nis", "my-event").sendTo(out, TextBodyRenderer())
    expect(out.toString()).toEqual("event: my-event\ndata: as\nis\n\n")
  }
}
