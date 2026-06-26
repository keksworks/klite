package klite.slf4j

import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEndWith
import ch.tutteli.atrium.api.fluent.en_GB.toStartWith
import ch.tutteli.atrium.api.verbs.expect
import klite.json.JsonMapper
import klite.json.parse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.event.Level.ERROR
import org.slf4j.event.Level.WARN
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.PrintStream
import java.net.InetAddress
import java.time.Year

class EcsJsonLoggerTest {
  val logger = EcsJsonLogger("hello.MyLogger")
  val out = ByteArrayOutputStream().also { KliteLogger.out = PrintStream(it) }
  @AfterEach fun restore() { KliteLogger.out = System.out }

  @Test fun minimal() {
    logger.print(WARN, "Hello\nWorld!", null)
    val json = out.toString()
    expect(json).toStartWith("{\"@timestamp\":\"" + Year.now())
      .toEndWith(""","trace.id":"Test worker","log.level":"warn","log.logger":"hello.MyLogger","message":"Hello\nWorld!","host.hostname":"${InetAddress.getLocalHost().hostName}"}""" + "\n")
    JsonMapper().parse<Any>(json)
  }

  @Test fun exception() {
    logger.print(ERROR, "Failed something", IOException("Kaboom"))
    val json = out.toString()
    expect(json).toContain(""""message":"Failed something","error.type":"java.io.IOException","error.message":"Kaboom","error.stack_trace":"klite.slf4j.EcsJsonLoggerTest.exception(""")
    JsonMapper().parse<Any>(json)
  }
}
