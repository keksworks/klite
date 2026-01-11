package test.klite.slf4j

import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEndWith
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toStartWith
import ch.tutteli.atrium.api.verbs.expect
import klite.json.JsonMapper
import klite.json.parse
import klite.slf4j.KliteLogger
import klite.slf4j.StackTraceOptimizingJsonLogger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets.UTF_8

class StackTraceOptimizingJsonLoggerTest {
  val out = ByteArrayOutputStream().also { KliteLogger.out = PrintStream(it) }

  @AfterEach fun restore() { KliteLogger.out = System.out }

  @Test fun `puts stack trace into json in prod for PaperTrail or LogTail`() {
    StackTraceOptimizingJsonLogger("MyLogger").print("", failHere())
    val json = out.toString(UTF_8)
    expect(json).toStartWith("""{"error":"java.lang.Exception: Hello\n\"World\"","stack":[".failHere(""")
      .toContain(javaClass.name)
      .toContain(""","cause":{"error":"java.lang.IllegalStateException: cause","stack":["""")
      .toEndWith("]}}\n")
    JsonMapper().parse<Any>(json)
  }

  @Test fun `empty stack`() {
    val noStackException = object: Exception() {
      override fun fillInStackTrace(): Throwable = this
    }
    StackTraceOptimizingJsonLogger("MyLogger").print("", noStackException)
    val json = out.toString(UTF_8)
    expect(json).toEqual("""{"error":"${noStackException::class.java.name}","stack":[]}""" + "\n")
    JsonMapper().parse<Any>(json)
  }

  fun failHere() = Exception("Hello\n\"World\"", IllegalStateException("cause"))
}
