package klite

import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThan
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThan
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.fluent.en_GB.toThrow
import ch.tutteli.atrium.api.verbs.expect
import klite.Decimal.Companion.ZERO
import org.junit.jupiter.api.Test
import java.text.NumberFormat
import java.util.*

class DecimalTest {
  @Test fun `operators & string`() {
    expect(20.d.toString()).toEqual("20")
    expect((3.d * 5).toString()).toEqual("15")
    expect((3.d / 5.0).toString()).toEqual("0.60")
    expect((10.d / 8).toString()).toEqual("1.25")
    expect((100.d / 97.d).toString()).toEqual("1.03")
    expect((100.d / 87.d).toString()).toEqual("1.15")
    expect((100.d / 87).toString()).toEqual("1.15")
    expect((-100.d / 87).toString()).toEqual("-1.15")
    expect((1.05.d / 1.05.d).toString()).toEqual("1")
    expect(100.d % 87.d).toEqual(13.d)
    expect((10.05.d % 8.03.d).toString()).toEqual("2.02")
    expect("1.33".d.toString()).toEqual("1.33")
    expect("1.2".d.toString()).toEqual("1.20")
    expect("145".d.toString()).toEqual("145")
    expect(-10.d).toEqual("-10".d)
    expect("-123.456".d).toEqual("-123.46".d)
    expect((-0.97.d).toString()).toEqual("-0.97")
  }

  @Test fun `min & max`() {
    expect(Decimal.MAX_VALUE.toString()).toEqual("92233720368547758.07")
    expect(Decimal.MIN_VALUE.toString()).toEqual("-92233720368547758.08")
  }

  @Test fun `inc & dec`() {
    var x = 1.d
    expect(++x).toEqual(2.d)
    expect(--x).toEqual(1.d)
  }

  @Test fun absoluteValue() {
    expect(1.d.absoluteValue).toEqual(1.d)
    expect((-1).d.absoluteValue).toEqual(1.d)
  }

  @Test fun sign() {
    expect("0.01".d.sign).toEqual(1)
    expect(ZERO.sign).toEqual(0)
    expect("-0.01".d.sign).toEqual(-1)
  }

  @Test fun overflow() {
    expect { Decimal.MAX_VALUE * 2.d }.toThrow<ArithmeticException>()
    expect { Decimal.MAX_VALUE + 1.d }.toThrow<ArithmeticException>()
    expect { Decimal.MIN_VALUE - 1.d }.toThrow<ArithmeticException>()
  }

  @Test fun compareTo() {
    expect(1.d).toBeGreaterThan(0.5.d)
    expect(2.d).toBeLessThan(2.5.d)
    expect(1.d.compareTo(1.00.d)).toEqual(0)
  }

  @Test fun percent() {
    expect(200.d percent 20.d).toEqual(40.d)
  }

  @Test fun formatting() {
    expect(NumberFormat.getNumberInstance(Locale.of("et")).format(10500.24.d)).toEqual("10 500,24")
  }
}
