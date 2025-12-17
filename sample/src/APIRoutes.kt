import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.tags.Tag
import klite.HttpExchange
import klite.MimeTypes
import klite.StatusCode
import klite.StatusCode.Companion.OK
import klite.annotations.*
import klite.i18n.translate
import kotlinx.coroutines.delay
import users.Id
import users.User
import users.UserRepository

@Path("/hello") @Tag(name = "cool", description = "Cool routes")
class APIRoutes(private val userRepository: UserRepository) {
  @GET @Operation(summary = "Just a hello") fun sayHello() = MyData("Hello")
  @GET("2") fun withExchange(exchange: HttpExchange) = "Hello2 ${exchange.method} ${exchange.path}"
  @GET("3") fun HttpExchange.asContext() = "${translate("hello")} $method $path"

  @GET("/user/:id") fun user(@PathParam id: Id<User>) = userRepository.get(id)

  @POST("/xml") @RequestBody(content = [Content(mediaType = "application/xml")])
  fun xml(@QueryParam hello: Boolean) = "OK"

  @GET("/suspend") suspend fun suspend(exchange: HttpExchange): String {
    delay(100)
    return "Suspend ${exchange.method} ${exchange.path}"
  }

  @GET("/suspend204") suspend fun suspendNoContent() = delay(50)
  @POST("/201") fun created() = StatusCode.Created
  @GET("/null") fun returnNull() = null

  @GET("/admin") @AdminOnly fun onlyForAdmins() = "Only for admins"
  @POST("/admin") @AdminOnly fun onlyForAdminsPost() = "Only for admins"
  @DELETE("/admin") @AdminOnly fun onlyForAdminsDelete() = "Only for admins"

  @GET("/params")
  fun withOptionalParams(@QueryParam optional: Boolean = false, @QueryParam required: String, @QueryParam nullable: String?) = "$optional,$required,$nullable"

  @GET("/broken-render")
  fun brokenRender() = object { lateinit var hello: String }

  @POST("/post")
  fun postExample(body: MyData, @QueryParam optional: Boolean = true) =
    "Received $body as json, optional = $optional"

  @PATCH("/patch")
  fun patchExample(body: MyData) =
    "Patched $body"

  @GET("/html")
  fun htmlBypassingRenderer(e: HttpExchange) {
    e.send(OK, "<h1>Hello</h1>", MimeTypes.html)
  }
}

data class MyData(val hello: String, val world: Double = Math.PI)
