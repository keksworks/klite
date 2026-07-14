package users

import klite.Email
import klite.HttpExchange
import klite.jdbc.ilike
import klite.jdbc.or
import klite.oauth.OAuthTokenResponse
import klite.oauth.OAuthUserProvider
import klite.oauth.UserProfile
import users.User.Name
import java.util.Locale.ENGLISH
import javax.sql.DataSource

class UserRepository(db: DataSource): CrudRepository<User>(db, "users"), OAuthUserProvider {
  fun search(q: String) = list(or(User::firstName ilike "%$q%", User::lastName ilike "%$q%", User::email ilike "%$q%"))

  fun by(email: Email): User? = list(User::email to email).firstOrNull()

  override fun provide(profile: UserProfile, tokenResponse: OAuthTokenResponse, exchange: HttpExchange) =
    by(profile.email) ?:
    User(profile.email, Name(profile.firstName, profile.lastName), profile.locale ?: ENGLISH, avatarUrl = profile.avatarUrl).also {
      save(it)
    }
}
