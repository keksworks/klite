package klite

import klite.Decimal.Companion.CENTS
import java.lang.Math.*
import kotlin.math.absoluteValue
import kotlin.math.roundToLong
import kotlin.math.sign

/**
 * A decimal fixed-point number with 2 fraction digits, suitable for e.g. monetary amounts.
 * Lighter and faster than BigDecimal and also has numeric equality, so that 10 == 10.0 == 10.00.
 * Operations resulting in Long overflow will throw ArithmeticException.
 */
class Decimal internal constructor(private val c: Long): Comparable<Decimal>, Number() {
  companion object {
    const val CENTS = 100L
    val MAX_VALUE = Decimal(Long.MAX_VALUE)
    val MIN_VALUE = Decimal(Long.MIN_VALUE)
    val ZERO = Decimal(0)
    val ONE = Decimal(CENTS)
  }

  constructor(v: Double): this((v * CENTS).roundToLong())
  constructor(v: String): this(v.toDouble())

  operator fun unaryMinus() = Decimal(-c)
  operator fun unaryPlus() = this

  operator fun plus(o: Decimal) = Decimal(addExact(c, o.c))
  operator fun minus(o: Decimal) = Decimal(subtractExact(c, o.c))
  operator fun times(o: Decimal) = Decimal(roundDiv(multiplyExact(c, o.c), CENTS))
  operator fun div(o: Decimal) = Decimal(roundDiv(multiplyExact(c, CENTS), o.c))
  operator fun rem(o: Decimal) = Decimal(c % o.c)

  operator fun times(o: Int) = Decimal(multiplyExact(c, o))
  operator fun times(o: Long) = Decimal(multiplyExact(c, o))
  operator fun times(o: Double) = Decimal(toDouble() * o)
  operator fun div(o: Int) = div(o.toLong())
  operator fun div(o: Long) = Decimal(roundDiv(c, o))
  operator fun div(o: Double) = Decimal(toDouble() / o)

  private fun roundDiv(n: Long, d: Long) = (if ((n < 0) xor (d < 0)) subtractExact(n, d/2) else addExact(n, d/2)) / d

  operator fun inc() = plus(1.d)
  operator fun dec() = minus(1.d)

  @Deprecated("Use absoluteValue instead", ReplaceWith("absoluteValue"))
  fun abs() = absoluteValue
  val absoluteValue get() = if (c < 0) Decimal(-c) else this
  val sign get() = c.sign

  infix fun percent(p: Decimal) = times(p) / CENTS

  override fun equals(other: Any?) = c == (other as? Decimal)?.c
  override fun hashCode() = c.hashCode()
  override fun compareTo(other: Decimal) = c.compareTo(other.c)

  override fun toString(): String {
    val sb = StringBuilder((c / CENTS).toString())
    val cents = (c % CENTS).absoluteValue
    if (cents > 0) sb.append('.').append(cents.toString().padStart(2, '0'))
    if (c > -CENTS && c < 0) sb.insert(0, '-')
    return sb.toString()
  }

  override fun toDouble() = c / CENTS.toDouble()
  override fun toFloat() = toDouble().toFloat()
  override fun toLong() = roundDiv(c, CENTS)
  override fun toInt() = toLong().toInt()
  override fun toShort() = toLong().toShort()
  override fun toByte() = toLong().toByte()
}

val Int.d get() = Decimal(multiplyExact(CENTS, this))
val Long.d get() = Decimal(multiplyExact(CENTS, this))
val Double.d get() = Decimal(this)
val Float.d get() = Decimal(toDouble())
val String.d get() = Decimal(this)

fun Iterable<Decimal>.sum() = sumOf { it }
fun Collection<Decimal>.average() = sum() / size.d

inline fun <T> Sequence<T>.sumOf(selector: (T) -> Decimal): Decimal = asIterable().sumOf(selector)
inline fun <T> Iterable<T>.sumOf(selector: (T) -> Decimal): Decimal {
  var sum = Decimal.ZERO
  for (element in this) sum += selector(element)
  return sum
}
