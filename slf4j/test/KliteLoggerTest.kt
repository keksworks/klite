package klite.slf4j

import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import klite.Config
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.event.Level.*
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.lang.Thread.currentThread

class KliteLoggerTest {
  val logger: Logger = KliteLogger("mega.cool.MyLogger")
  val out = ByteArrayOutputStream().also { KliteLogger.out = PrintStream(it) }
  @AfterEach fun restore() { KliteLogger.out = System.out }

  @Test fun findInheritedLevel() {
    expect(KliteLogger("mega.cool.MyLogger").level).toEqual(INFO)
    Config["LOGGER.mega"] = DEBUG.name
    expect(KliteLogger("mega.cool.MyLogger").level).toEqual(DEBUG)
    Config["LOGGER.mega.cool"] = WARN.name
    expect(KliteLogger("mega.cool.MyLogger").level).toEqual(WARN)
    Config["LOGGER.mega.cool.MyLogger"] = INFO.name
    expect(KliteLogger("mega.cool.MyLogger").level).toEqual(INFO)
  }

  @Test fun isLevelEnabled() {
    expect(logger.isTraceEnabled).toEqual(false)
    expect(logger.isDebugEnabled).toEqual(false)
    expect(logger.isInfoEnabled).toEqual(true)
    expect(logger.isWarnEnabled).toEqual(true)
    expect(logger.isErrorEnabled).toEqual(true)
  }

  @Test fun logs() {
    logger.info("Hello")
    expect(out.toString("UTF-8")).toContain(" [${currentThread().name}] INFO MyLogger - Hello")
  }

  @Test fun `does not log`() {
    logger.debug("Hello")
    expect(out.toString("UTF-8")).toBeEmpty()
  }

  @Test fun `logs stack trace`() {
    logger.info("Failed", Exception("Kaboom"))
    expect(out.toString("UTF-8")).toContain("MyLogger - Failed", "java.lang.Exception: Kaboom", "\tat ")
  }

  @Test fun formats() {
    logger.info("Hello {}", "World")
    expect(out.toString("UTF-8")).toContain("- Hello World")
  }
}
