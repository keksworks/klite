package klite.html

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test

class HelpersTest {
  @Test fun escapeJs() {
    expect("plain".escapeJs()).toEqual("plain")
    expect("it's".escapeJs()).toEqual("it\\'s")
    expect("say \"hi\"".escapeJs()).toEqual("say \\\"hi\\\"")
    expect("back\\slash".escapeJs()).toEqual("back\\\\slash")
    expect("</script>".escapeJs()).toEqual("\\u003c/script>")
  }

  @Test fun `escapeJs cannot break out of single quotes`() {
    expect("x\\');alert(1)//".escapeJs()).toEqual("x\\\\\\');alert(1)//")
  }

  @Test fun escapeHtml() {
    expect("<b>&\"'</b>".escapeHtml()).toEqual("&lt;b&gt;&amp;&quot;&#39;&lt;/b&gt;")
  }
}
