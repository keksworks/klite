package klite.oauth

import klite.Email
import klite.HttpExchange

interface OAuthUser {
  val email: Email
  val firstName: String
  val lastName: String
  val id: Any?
}

interface OAuthUserProvider {
  fun provide(profile: UserProfile, tokenResponse: OAuthTokenResponse, exchange: HttpExchange): OAuthUser

  fun initSession(user: OAuthUser, exchange: HttpExchange) = exchange.initSession(user)
}
