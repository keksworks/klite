package klite.oauth

import klite.*
import klite.http.authBearer
import klite.json.*
import java.net.URI
import java.net.http.HttpClient
import java.util.*

abstract class OAuthClient(provider: String? = null, scope: String, authUrl: String, tokenUrl: String, profileUrl: String? = null, jwkKeysUrl: String? = null, httpClient: HttpClient) {
  protected open val http = JsonHttpClient(json = JWT.jsonMapper, http = httpClient)
  val provider = provider ?: this::class.simpleName!!.substringBefore(OAuthClient::class.simpleName!!).uppercase()

  val clientId = configRequired("CLIENT_ID")
  private val clientSecret = configRequired("CLIENT_SECRET")

  val scope = config("SCOPE") ?: scope
  val authUrl = config("AUTH_URL") ?: authUrl
  val tokenUrl = config("TOKEN_URL") ?: tokenUrl
  val profileUrl = config("PROFILE_URL") ?: profileUrl
  val jwkKeysUrl = config("KEYS_URL") ?: jwkKeysUrl

  protected fun configRequired(name: String) = Config.required(provider + "_OAUTH_" + name)
  protected fun config(name: String) = Config.optional(provider + "_OAUTH_" + name)

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
    http.post<OAuthTokenResponse>(tokenUrl, urlEncodeParams(
      "grant_type" to grantType,
      (if (grantType == "authorization_code") "code" else grantType) to code,
      "client_id" to clientId,
      "client_secret" to clientSecret,
      "redirect_uri" to redirectUrl?.toString()
    )) {
      setHeader("Content-Type", MimeTypes.withCharset(MimeTypes.wwwForm))
    }.also { it.idToken?.verify() }

  protected suspend fun fetchProfileResponse(token: OAuthTokenResponse): JsonNode = http.get(profileUrl!!) { authBearer(token.accessToken) }

  abstract suspend fun profile(token: OAuthTokenResponse, exchange: HttpExchange): UserProfile

  protected fun JsonNode.getLocale(key: String = "locale") = getOrNull<String>(key)?.let { Locale.forLanguageTag(it) }

  protected suspend fun readKeys() = http.get<JwksKeysResponse>(jwkKeysUrl!!).keys.associateBy { it.kid }

  private lateinit var keys: Map<String, JwkKey>
  suspend fun key(kid: String): JwkKey {
    if (!this::keys.isInitialized) keys = readKeys()
     return keys[kid] ?: error("Key with kid=$kid not found")
  }
}

/** https://console.cloud.google.com/apis/credentials */
class GoogleOAuthClient(httpClient: HttpClient): OAuthClient(
  scope = "email profile",
  authUrl = "https://accounts.google.com/o/oauth2/v2/auth",
  tokenUrl = "https://oauth2.googleapis.com/token",
  profileUrl = "https://www.googleapis.com/oauth2/v1/userinfo?alt=json",
  jwkKeysUrl = "https://www.googleapis.com/oauth2/v3/certs",
  httpClient = httpClient
) {
  override suspend fun profile(token: OAuthTokenResponse, exchange: HttpExchange): UserProfile {
    val res = fetchProfileResponse(token)
    val email = Email(res.getString("email"))
    return UserProfile(provider, res.getString("id"), email,
      res.getOrNull("givenName") ?: email.value.substringBefore("@").capitalize(), res.getOrNull("familyName") ?: "",
      res.getOrNull<String>("picture")?.let { URI(it) }, res.getLocale())
  }
}

/** https://portal.azure.com/ */
class MicrosoftOAuthClient(httpClient: HttpClient): OAuthClient(
  scope = "email openid offline_access User.Read",
  authUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize",
  tokenUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/token",
  profileUrl = "https://graph.microsoft.com/v1.0/me",
  jwkKeysUrl = "https://login.microsoftonline.com/common/discovery/v2.0/keys",
  httpClient = httpClient
) {
  override suspend fun profile(token: OAuthTokenResponse, exchange: HttpExchange): UserProfile {
    val res = fetchProfileResponse(token)
    val email = res.getOrNull("mail") ?: res.getOrNull<String>("userPrincipalName") ?: error("Cannot obtain user's email")
    return UserProfile(provider, res.getString("id"), Email(email), res.getOrNull("givenName") ?: email.substringBefore("@").capitalize(), res.getOrNull("surname") ?: "",
      locale = res.getLocale("preferredLanguage"))
  }
}

/** https://developers.facebook.com/apps/ */
class FacebookOAuthClient(httpClient: HttpClient): OAuthClient(
  scope = "email public_profile",
  authUrl = "https://www.facebook.com/v22.0/dialog/oauth",
  tokenUrl = "https://graph.facebook.com/v22.0/oauth/access_token",
  profileUrl = "https://graph.facebook.com/v22.0/me?fields=id,first_name,last_name,email,picture",
  jwkKeysUrl = "https://www.facebook.com/.well-known/oauth/openid/jwks/",
  httpClient = httpClient
) {
  override suspend fun profile(token: OAuthTokenResponse, exchange: HttpExchange): UserProfile {
    val res = fetchProfileResponse(token)
    val avatarData = res.getOrNull<JsonNode>("picture")?.getOrNull<JsonNode>("data")
    val avatarExists = avatarData?.getOrNull<Boolean>("is_silhouette") != true
    val email = Email(res.getString("email"))
    return UserProfile(provider, res.getString("id"),
      email, res.getOrNull("firstName") ?: email.value.substringBefore("@").capitalize(), res.getOrNull("lastName") ?: "",
      avatarData?.getOrNull<String>("url")?.takeIf { avatarExists }?.let { URI(it) },
      res.getLocale())
  }
}

/** https://developer.apple.com/acount/resources/authkeys/ */
class AppleOAuthClient(httpClient: HttpClient): OAuthClient(
  scope = "email name",
  authUrl = "https://appleid.apple.com/auth/authorize",
  tokenUrl = "https://appleid.apple.com/auth/token",
  jwkKeysUrl = "https://appleid.apple.com/auth/keys",
  httpClient = httpClient
) {
  override suspend fun profile(token: OAuthTokenResponse, exchange: HttpExchange): UserProfile {
    val email = token.idToken!!.payload.email!!
    val user = exchange.bodyParams["user"]?.let { http.json.parse<AppleUserProfile>(it.toString()) }
    return UserProfile(provider, token.idToken.payload.subject, email, user?.name?.firstName ?: email.value.substringBefore("@").capitalize(), user?.name?.lastName ?: "")
  }

  data class AppleUserProfile(val name: AppleUserName, val email: Email)
  data class AppleUserName(val firstName: String, val lastName: String)
}

data class OAuthTokenResponse(val accessToken: String, val expiresIn: Int, val scope: String? = null, val tokenType: String? = null, val idToken: JWT? = null, val refreshToken: String? = null)
data class UserProfile(val provider: String, override val id: String, override val email: Email, override val firstName: String, override val lastName: String, val avatarUrl: URI? = null, val locale: Locale? = null): OAuthUser
