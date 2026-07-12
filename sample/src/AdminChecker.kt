import klite.Before
import klite.ForbiddenException
import klite.HttpExchange
import kotlin.reflect.full.hasAnnotation

@Target(AnnotationTarget.FUNCTION)
annotation class AdminOnly

class AdminChecker: Before {
  override fun before(exchange: HttpExchange) {
    // implement whatever access logic you need in your app
    if (exchange.route.hasAnnotation<AdminOnly>() && exchange.header("I-Am-Admin") == null)
      throw ForbiddenException("Admin only")
  }
}
