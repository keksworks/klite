package klite.slf4j

import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toEndWith
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toStartWith
import ch.tutteli.atrium.api.verbs.expect
import klite.json.JsonMapper
import klite.json.JsonNode
import klite.json.parse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
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
    JsonMapper().parse<JsonNode>(json)
  }

  @Test fun `escapes backslashes and control chars so values can't inject fields`() {
    logger.print(WARN, "GET /x\\\",\"log.level\":\"info\u0001", null)
    val json = JsonMapper().parse<JsonNode>(out.toString())
    expect(json["log.level"] as Any).toEqual("warn")
    expect(json["message"] as Any).toEqual("GET /x\\\",\"log.level\":\"info\u0001")
  }

  @Test fun exception() {
    logger.print(ERROR, "Failed something", IOException("Kaboom"))
    val json = out.toString()
    expect(json).toContain(""""message":"Failed something","error.type":"java.io.IOException","error.message":"Kaboom","error.stack_trace":"klite.slf4j.EcsJsonLoggerTest.exception(""")
    JsonMapper().parse<JsonNode>(json)
  }

  @Test fun mdc() {
    RequestMDCContext().run {
      MDC.put("mdc.1", "value")
      MDC.put("mdc.2", "Hello\nWorld")
      logger.print(ERROR, null, null)
      val json = out.toString()
      expect(json).toContain(""""mdc.1":"value","mdc.2":"Hello\nWorld",""")
      JsonMapper().parse<JsonNode>(json)
    }
  }
}
