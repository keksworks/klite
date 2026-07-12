package klite

typealias Handler = HttpExchange.() -> Any?
typealias Decorator = (exchange: HttpExchange, handler: Handler) -> Any?

fun Decorator.wrap(handler: Handler): Handler = { invoke(this, handler) }
fun List<Decorator>.wrap(handler: Handler) = foldRight(handler) { d, h -> d.wrap(h) }

fun interface Before {
  fun before(exchange: HttpExchange)
}

fun Before.toDecorator(): Decorator = { ex, handler ->
  before(ex); handler(ex)
}

fun interface After {
  fun after(exchange: HttpExchange, error: Throwable?)
}

fun After.toDecorator(): Decorator = { ex, handler ->
  var error: Throwable? = null
  try { handler(ex) }
  catch (e: Throwable) { error = e; throw e }
  finally { after(ex, error) }
}

fun Registry.requireAllDecorators() =
  requireAll<Before>().map { it.toDecorator() } + requireAll() + requireAll<After>().map { it.toDecorator() }
