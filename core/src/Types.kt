package klite

import java.security.MessageDigest

/** Base class for String-based normalized value types, handled automatically by [Converter] */
abstract class StringValue(val value: String) {
  override fun toString() = value
  override fun equals(other: Any?) = other != null && javaClass == other.javaClass && value == (other as StringValue).value
  override fun hashCode() = value.hashCode()
}

class Email(email: String): StringValue(email.trim().lowercase()) {
  companion object {}
  init { require(value.length > 3 && value.contains("@")) { "Invalid email: $email" } }
  val domain get() = value.substringAfter("@")
}

class Phone(phone: String): StringValue(phone.replace(removeChars, "")) {
  companion object {
    private val removeChars = "[\\s-()]+".toRegex()
    private val valid = "\\+[0-9]{9,}".toRegex()
  }
  init { require(valid.matches(value)) {
    "International phone number should start with + and have at least 9 digits: $value" }
  }
}

class Password(value: String): StringValue(value) {
  /** constant-time comparison, so that password checks don't leak the value via timing */
  override fun equals(other: Any?) =
    other is Password && MessageDigest.isEqual(value.toByteArray(), other.value.toByteArray())

  override fun toString() = "Password<***>"
}
