package users

import klite.Email
import klite.jdbc.Column
import klite.jdbc.FlattenColumns
import klite.jdbc.UpdatableEntity
import klite.oauth.OAuthUser
import java.net.URI
import java.time.Instant
import java.util.*

data class User(
  override val email: Email,
  @FlattenColumns val name: Name,
  val locale: Locale,
  val passwordHash: String? = null,
  val avatarUrl: URI? = null,
  override var id: Id<User>? = null,
  override var updatedAt: Instant? = null
): Entity<User>, UpdatableEntity, OAuthUser {
  override val firstName get() = name.first
  override val lastName get() = name.last

  data class Name(@Column("firstName") val first: String, @Column("lastName") val last: String)

  data class Address(
    val userId: Id<User>,
    val city: String,
    val countryCode: String,
    override var id: Id<Address>? = null,
    override var updatedAt: Instant? = null
  ): Entity<Address>
}
