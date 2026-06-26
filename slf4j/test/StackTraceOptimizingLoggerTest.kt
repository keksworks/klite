package klite.slf4j

import ch.tutteli.atrium.api.fluent.en_GB.notToContain
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEndWith
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.mockk
import klite.Decorator
import klite.Handler
import klite.wrap
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.slf4j.event.Level.INFO
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class StackTraceOptimizingLoggerTest {
  @Test fun `cuts stack trace until klite`() {
    val out = ByteArrayOutputStream()
    KliteLogger.out = PrintStream(out)
    val decorator: Decorator = { e, h -> h(e) }
    val handler: Handler = {
      val logger = StackTraceOptimizingLogger("MyLogger")
      logger.print(INFO, "Just message", null)
      expect(out.toString()).toEndWith("INFO MyLogger - Just message\n")

      logger.print(INFO, "Message", Exception("Hello", IllegalStateException("cause")))
      expect(out.toString()).toContain("Message: java.lang.Exception: Hello", "  at klite.")
        .toContain("Caused by: java.lang.IllegalStateException: cause")
        .notToContain("  at ${javaClass.name}.cuts")
        .toEndWith("\n")
    }
    runBlocking { decorator.wrap(handler).invoke(mockk()) }
    KliteLogger.out = System.out
  }
}
