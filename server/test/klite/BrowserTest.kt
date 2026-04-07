package klite

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test

class BrowserTest {
  @Test fun `detects browser name and version`() {
    expect(detectBrowser("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.88 Safari/537.36")).toEqual("Chrome/100.0.4896.88")
    expect(detectBrowser("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.4 Safari/605.1.15")).toEqual("Safari/15.4")
    expect(detectBrowser("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.127 Safari/537.36 Edg/100.0.1185.50")).toEqual("Edg/100.0.1185.50")
    expect(detectBrowser("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:99.0) Gecko/20100101 Firefox/99.0")).toEqual("Firefox/99.0")
    expect(detectBrowser("Mozilla/5.0+(compatible; UptimeRobot/2.0; http://www.uptimerobot.com/)")).toEqual("UptimeRobot/2.0")
    expect(detectBrowser("Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)")).toEqual("Googlebot/2.1")
    expect(detectBrowser("Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1")).toEqual("Mobile/Safari/13.0.3")
    expect(detectBrowser("Mozilla/5.0 (iPhone; CPU iPhone OS 17_3_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148")).toEqual("Mobile/AppleWebKit/605.1.15")
    expect(detectBrowser("Mozilla/5.0 (iPhone; CPU iPhone OS 10_3 like Mac OS X) AppleWebKit/602.1.50 (KHTML, like Gecko) CriOS/56.0.2924.75 Mobile/14E5239e Safari/602.1")).toEqual("Mobile/CriOS/56.0.2924.75")
    expect(detectBrowser("Mozilla/5.0 (iPhone; CPU iPhone OS 14_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0 EdgiOS/46.2.0 Mobile/15E148 Safari/605.1.15")).toEqual("Mobile/EdgiOS/46.2.0")
    expect(detectBrowser("Mozilla/5.0 (iPhone; CPU iPhone OS 14_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) FxiOS/123.0 Mobile/15E148 Safari/605.1.15")).toEqual("Mobile/FxiOS/123.0")
    expect(detectBrowser("Mozilla/5.0 (Linux; Android 6.0.1; Nexus 5X Build/MMB29P) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Mobile Safari/537.36 (compatible; Googlebot/2.1; +https://www.google.com/bot.html)")).toEqual("Googlebot/2.1/Mobile/Chrome/146")
  }
}
