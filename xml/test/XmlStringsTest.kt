package klite.xml

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

class XmlStringsTest {
  @Test fun dropXmlHeader() {
    expect("<?xml?>\n<root/>".dropXmlHeader()).toEqual("<root/>")
  }

  @Test fun dropXmlRoot() {
    expect("<?xml?>\n<root><content/></root>".dropXmlRoot()).toEqual("<content/>")
  }

  @Test fun extractXmlTag() {
    @Language("xml") val xml = "<root><content>Hello</content></root>"
    expect(xml.extractXmlTag("content")).toEqual("<content>Hello</content>")
  }

  @Test fun extractXmlTagStrippingNamespaces() {
    @Language("xml") val xml = "<root xmlns:udt=\"UDT:NS\"><ns:content><udt:DateTimeString><value>123</value></udt:DateTimeString></ns:content></root>"
    expect(xml.extractXmlTag("content"))
      .toEqual("<content><DateTimeString><value>123</value></DateTimeString></content>")
  }

  @Test fun extractXmlTagPreservingNamespace() {
    @Language("xml") val xml = "<root xmlns:udt=\"UDT:NS\"><ns:content>\n<udt:DateTimeString><value>123</value></udt:DateTimeString>\n</ns:content></root>"
    expect(xml.extractXmlTag("content", preserveNs = setOf("UDT:NS")))
      .toEqual("<content xmlns:udt=\"UDT:NS\">\n<udt:DateTimeString><value>123</value></udt:DateTimeString>\n</content>")
  }

  @Test fun extractXmlTagThatHasNamespace() {
    @Language("xml") val xml = "<uilResponse xmlns=\"http://efti.eu/v1/edelivery\" requestId=\"123123123123\" status=\"200\"><consignment xmlns=\"http://efti.eu/v1/consignment/common\">data</consignment></uilResponse>"
    expect(xml.extractXmlTag("consignment"))
      .toEqual("<consignment xmlns=\"http://efti.eu/v1/consignment/common\">data</consignment>")
  }

  @Test fun extractRootTagAlreadyWithNamespace() {
    @Language("xml") val xml = "<root xmlns:udt=\"UDT:NS\"><udt:content>Blah</udt:content></root>"
    expect(xml.extractXmlTag("root", setOf("UDT:NS"))).toEqual(xml)
  }

  @Test fun `tag name is treated literally, not as a regex`() {
    @Language("xml") val xml = "<root><axb>wild</axb><content>Hello</content></root>"
    expect(xml.extractXmlTag("content")).toEqual("<content>Hello</content>")
    // `.` must not act as a wildcard, `|` must not alternate, and quantifiers must not apply
    expect { xml.extractXmlTag("a.b") }.toThrow<IllegalStateException>()
    expect { xml.extractXmlTag("content|axb") }.toThrow<IllegalStateException>()
    expect { xml.extractXmlTag(".*") }.toThrow<IllegalStateException>()
    expect { xml.extractXmlTag("(a+)+") }.toThrow<IllegalStateException>()
  }

  @Test fun `a literal dot still matches a dotted tag name`() {
    @Language("xml") val xml = "<root><a.b>dot</a.b></root>"
    expect(xml.extractXmlTag("a.b")).toEqual("<a.b>dot</a.b>")
  }
}
