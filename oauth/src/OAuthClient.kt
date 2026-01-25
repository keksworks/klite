package klite.oauth

import klite.*
import klite.SnakeCase
import klite.http.authBearer
import klite.json.*
import java.net.URI
import java.net.http.HttpClient
import java.util.*

abstract class OAuthClient(scope: String, authUrl: String, tokenUrl: String, profileUrl: String, httpClient: HttpClient) {
  protected open val http = JsonHttpClient(json = JsonMapper(keys = SnakeCase, trimToNull = false), http = httpClient)
  val provider = this::class.simpleName!!.substringBefore(OAuthClient::class.simpleName!!).uppercase()

  val clientId = config("CLIENT_ID")
  private val clientSecret = config("CLIENT_SECRET")

  val scope = config("SCOPE", scope)
  val authUrl = config("AUTH_URL", authUrl)
  val tokenUrl = config("TOKEN_URL", tokenUrl)
  val profileUrl = config("PROFILE_URL", profileUrl)

  protected fun config(name: String) = Config.required(provider + "_OAUTH_" + name)
  protected fun config(name: String, default: String) = Config.optional(provider + "_OAUTH_" + name, default)

  open fun startAuthUrl(state: String?, redirectUrl: URI, lang: String) = URI(authUrl) + mapOfNotNull(
    "response_type" to "code",
    "response_mode" to "form_post",
    "redirect_uri" to redirectUrl,
    "client_id" to clientId,
    "scope" to scope,
    "access_type" to "offline",
    "state" to state,
    "hl" to lang,
    "prompt" to "select_account"
  )

  suspend fun authenticate(code: String, redirectUrl: URI) = fetchTokenResponse("authorization_code", code, redirectUrl)
  suspend fun refresh(refreshToken: String) = fetchTokenResponse("refresh_token", refreshToken)

  protected open suspend fun fetchTokenResponse(grantType: String, code: String, redirectUrl: URI? = null): OAuthTokenResponse =
    http.post(tokenUrl, urlEncodeParams(mapOf(
      "grant_type" to grantType,
      (if (grantType == "authorization_code") "code" else grantType) to code,
      "client_id" to clientId,
      "client_secret" to clientSecret,
      "redirect_uri" to redirectUrl?.toString()
    ))) {
      setHeader("Content-Type", MimeTypes.withCharset(MimeTypes.wwwForm))
    }

  protected suspend fun fetchProfileResponse(token: OAuthTokenResponse): JsonNode = http.get(profileUrl) { authBearer(token.accessToken) }

  abstract suspend fun profile(token: OAuthTokenResponse, exchange: HttpExchange): UserProfile

  protected fun JsonNode.getLocale(key: String = "locale") = getOrNull<String>(key)?.let { Locale.forLanguageTag(it) }
}

/** https://console.cloud.google.com/apis/credentials */
class GoogleOAuthClient(httpClient: HttpClient): OAuthClient(
  "email profile",
  "https://accounts.google.com/o/oauth2/v2/auth",
  "https://oauth2.googleapis.com/token",
  "https://www.googleapis.com/oauth2/v1/userinfo?alt=json",
  httpClient
) {
  override suspend fun profile(token: OAuthTokenResponse, exchange: HttpExchange): UserProfile {
    val res = fetchProfileResponse(token)
    val email = Email(res.getString("email"))
    return UserProfile(provider, res.getString("id"), email,
      res.getOrNull("givenName") ?: email.extractName(), res.getOrNull("familyName") ?: "",
      res.getOrNull<String>("picture")?.let { URI(it) }, res.getLocale())
  }
}

/** https://portal.azure.com/ */
class MicrosoftOAuthClient(httpClient: HttpClient): OAuthClient(
  "email openid offline_access User.Read",
  "https://login.microsoftonline.com/common/oauth2/v2.0/authorize",
  "https://login.microsoftonline.com/common/oauth2/v2.0/token",
  "https://graph.microsoft.com/v1.0/me",
  httpClient
) {
  override suspend fun profile(token: OAuthTokenResponse, exchange: HttpExchange): UserProfile {
    val res = fetchProfileResponse(token)
    val email = Email(res.getOrNull("mail") ?: res.getOrNull<String>("userPrincipalName") ?: error("Cannot obtain user's email"))
    return UserProfile(provider, res.getString("id"), email, res.getOrNull("givenName") ?: email.extractName(), res.getOrNull("surname") ?: "",
      locale = res.getLocale("preferredLanguage"))
  }
}

/** https://developers.facebook.com/apps/ */
class FacebookOAuthClient(httpClient: HttpClient): OAuthClient(
  "email public_profile",
  "https://www.facebook.com/v12.0/dialog/oauth",
  "https://graph.facebook.com/v12.0/oauth/access_token",
  "https://graph.facebook.com/v12.0/me?fields=id,first_name,last_name,email,picture,locale",
  httpClient
) {
  override suspend fun profile(token: OAuthTokenResponse, exchange: HttpExchange): UserProfile {
    val res = fetchProfileResponse(token)
    val avatarData = res.getOrNull<JsonNode>("picture")?.getOrNull<JsonNode>("data")
    val avatarExists = avatarData?.getOrNull<Boolean>("is_silhouette") != true
    val email = Email(res.getString("email"))
    return UserProfile(provider, res.getString("id"),
      email, res.getOrNull("firstName") ?: email.extractName(), res.getOrNull("lastName") ?: "",
      avatarData?.getOrNull<String>("url")?.takeIf { avatarExists }?.let { URI(it) },
      res.getLocale())
  }
}

/** https://developer.apple.com/acount/resources/authkeys/ */
class AppleOAuthClient(httpClient: HttpClient): OAuthClient(
  "email name",
  "https://appleid.apple.com/auth/authorize",
  "https://appleid.apple.com/auth/token",
  "",
  httpClient
) {
  override suspend fun profile(token: OAuthTokenResponse, exchange: HttpExchange): UserProfile {
    val email = token.idToken!!.payload.email!!
    val user = exchange.bodyParams["user"]?.let { http.json.parse<AppleUserProfile>(it.toString()) }
    return UserProfile(provider, token.idToken.payload.subject, email, user?.name?.firstName ?: email.extractName(), user?.name?.lastName ?: "")
  }

  data class AppleUserProfile(val name: AppleUserName, val email: Email)
  data class AppleUserName(val firstName: String, val lastName: String)
}

data class OAuthTokenResponse(val accessToken: String, val expiresIn: Int, val scope: String? = null, val tokenType: String? = null, val idToken: JWT? = null, val refreshToken: String? = null)
data class UserProfile(val provider: String, override val id: String, override val email: Email, override val firstName: String, override val lastName: String, val avatarUrl: URI? = null, val locale: Locale? = null): OAuthUser

internal fun Email.extractName(): String {
  val localPart = value.substringBefore("@")
  return localPart.replaceFirstChar {
    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
  }
}
