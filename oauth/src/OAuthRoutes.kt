package klite.oauth

import klite.*
import klite.annotations.BodyParam
import klite.annotations.GET
import klite.annotations.POST
import klite.annotations.PathParam
import klite.i18n.lang
import java.net.URI
import java.util.*

open class OAuthRoutes(private val userProvider: OAuthUserProvider, registry: Registry) {
  private val clients = registry.requireAll<OAuthClient>().associateBy { it.provider }

  private fun client(provider: String?) = (if (provider == null) clients.values.firstOrNull() else clients[provider.uppercase()]) ?:
    error("No ${provider ?: ""}OAuthClient registered")

  @GET open suspend fun start(e: HttpExchange) = start(null, e)

  @GET("/:provider") open suspend fun start(@PathParam provider: String?, e: HttpExchange) =
    if (e.query("error_message") != null) e.redirectToLogin(null, "oauthProviderRefused")
    else if (e.query("state") != null) accept(provider, e.query("code"), e.query("state")!!, e)
    else {
      val nonce = Math.random().toString().substringAfter(".")
      e.session["oauth_$nonce"] = e.safeRedirectParam?.toString() ?: "/"
      e.redirect(client(provider).startAuthUrl(nonce, e.fullUrl(e.path), e.lang))
    }

  @POST open suspend fun accept(@BodyParam code: String?, @BodyParam state: String?, e: HttpExchange) = accept(null, code, state, e)

  @POST("/:provider") open suspend fun accept(@PathParam provider: String?, @BodyParam code: String?, @BodyParam state: String?, e: HttpExchange) {
    val nonceKey = "oauth_$state"
    val originalUrl = e.session[nonceKey]?.let { URI(it) } ?: throw ForbiddenException("Invalid or expired OAuth state")
    e.session[nonceKey] = null

    if (code == null) e.redirectToLogin(originalUrl, "userCancelled")

    val client = client(provider)
    val token = client.authenticate(code, e.fullUrl(e.path))
    var profile = client.profile(token, e)
    if (profile.locale == null) profile = profile.copy(locale = Locale.forLanguageTag(e.lang))

    val user = userProvider.provide(profile, token, e)
    e.initSession(user)
    e.redirect(originalUrl)
  }
}

val HttpExchange.safeRedirectParam get() = query("redirect")?.takeIf { it.startsWith("/") || it.startsWith(fullUrl("/").toString()) }?.let { URI(it) }

private fun HttpExchange.redirectToLogin(originalUrl: URI? = fullUrl, errorKey: String? = null): Nothing =
  redirect(fullUrl(contextPath) + mapOfNotNull("redirect" to originalUrl, "errorKey" to errorKey))
