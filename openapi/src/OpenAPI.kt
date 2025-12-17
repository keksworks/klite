package klite.openapi

import io.swagger.v3.oas.annotations.Hidden
import klite.*
import klite.RequestMethod.GET
import klite.StatusCode.Companion.OK
import klite.html.escapeJs
import org.intellij.lang.annotations.Language

/**
 * Adds /openapi endpoints to the context, listing all the preceding routes.
 * This handler will try to gather all the information about request/parameters/response automatically, but you can use Swagger annotations to specify more details.
 * - Pass [@OpenAPIDefinition][io.swagger.v3.oas.annotations.OpenAPIDefinition] or only [@Info][io.swagger.v3.oas.annotations.info.Info] annotation to this function to specify general info
 * - Pass [@SecurityScheme][io.swagger.v3.oas.annotations.security.SecurityScheme] to define authorization
 * - Use [@Operation][io.swagger.v3.oas.annotations.Operation] annotation to describe each route
 * - Use [@SecurityRequirement][io.swagger.v3.oas.annotations.security.SecurityRequirement] annotations to reference security requirements of the route
 * - Use [@RequestBody][io.swagger.v3.oas.annotations.parameters.RequestBody] annotations to define the request body
 * - Use [@ApiResponse][io.swagger.v3.oas.annotations.responses.ApiResponse] annotations to define one or many possible responses
 * - Use [@Hidden][io.swagger.v3.oas.annotations.Hidden] to hide a route from the spec
 * - [@Parameter][io.swagger.v3.oas.annotations.Parameter] annotation can be used on method parameters directly
 * - [@Tag][io.swagger.v3.oas.annotations.tags.Tag] annotation is supported on route classes for grouping of routes
 */
// Generate entities separately, and reference them. To enable TS generation
// TODO: support @Schema(description on data classes and fields)
context(Server)
fun Router.openApi(path: String = "/openapi", annotations: List<Annotation> = emptyList(), swaggerUIConfig: Map<String, Comparable<*>> = emptyMap()) {
  val hidden = annotations + Hidden()
  add(Route(GET, pathParamRegexer.from("$path.json"), hidden) { generateOpenAPI() })
  add(Route(GET, pathParamRegexer.from("$path.html"), hidden) { swaggerUI(path, swaggerUIConfig) })
  add(Route(GET, pathParamRegexer.from(path), hidden) {
    if (accept(MimeTypes.html)) swaggerUI(path, swaggerUIConfig)
    else generateOpenAPI()
  })
}

@Language("html")
private fun HttpExchange.swaggerUI(path: String, config: Map<String, Comparable<*>> = emptyMap()) = send(OK, """
  <!DOCTYPE html>
  <html lang="en">
  <head>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="description" content="SwaggerUI">
    <title>SwaggerUI</title>
    <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist/swagger-ui.css">
  </head>
  <body>
    <div id="swagger-ui"></div>
    <script src="https://unpkg.com/swagger-ui-dist/swagger-ui-bundle.js" crossorigin></script>
    <script>
      onload = () => {
        ui = SwaggerUIBundle({
          url: '${path.substringAfter("/")}.json',
          dom_id: '#swagger-ui',
          ${config.entries.joinToString {
            "'${it.key.escapeJs()}': ${if (it.value !is String) "${it.value}" else "'${it.value.toString().escapeJs()}'" }"
          }}
        })
      }
    </script>
  </body>
  </html>
""", MimeTypes.html)
