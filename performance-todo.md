# Performance todo

Findings from a hot-path scan (JSON, JDBC, server). Ordered by impact.

## High impact (per-request / per-object)

### 1. Uncached reflection annotations in JSON path — measured, low impact
`json/src/JsonRenderer.kt:107-108`, `json/src/JsonParser.kt:74-76`

```kotlin
internal val <T: Any> Sequence<KProperty1<T, *>>.notIgnored get() = filter { !it.hasAnnotation<JsonIgnore>() }
internal val KProperty1<*, *>.jsonName get() = findAnnotation<JsonProperty>()?.value?.trimToNull() ?: name
```

Every rendered property does `hasAnnotation` + `findAnnotation` again. JDBC already caches this (`colNameCache` in `JdbcExtensions.kt:220`); JSON does not. `EntityBenchmarkTest` calls `findAnnotation` “the core bottleneck”.

Measured: each `findAnnotation`/`hasAnnotation` is ~40ns, rendering an object is only ~6% slower than the same data as a Map, so a metadata cache is not worth the complexity. The parser now caches properties by json name per class (`jsonProperties`).

### 2. `unboxInline()` re-resolves a Method every call - done
`core/src/Extensions.kt:20`

```kotlin
fun Any.unboxInline() = javaClass.getMethod("unbox-impl").invoke(this)
```

`getMethod` + reflective `invoke` on every value-class unbox (JSON write, JDBC convert). Cache the `Method` per class (same pattern as `pgObjectGetValue` in `ResultSets.kt:34`).

### 3. Per-request `KFunction.call` for `HttpExchange` — done
`server/src/klite/Server.kt:90`

```kotlin
httpExchangeCreator.call(ex, config, sessionStore, requestId).handler()
```

Kotlin-reflect call on every request. Prefer a captured constructor/lambda `(ex, config, session, id) -> HttpExchange(...)`.

### 4. Dynamic `getMethod` on every joined-column read — done
`jdbc/src/ResultSets.kt:87`

```kotlin
else ResultSet::class.java.getMethod(method.name, Integer.TYPE, *method.parameterTypes.drop(1).toTypedArray())
  .invoke(rs, index, *args.drop(1).toTypedArray())
```

Method lookup is rebuilt per call in `PrefixedColumns`. Cache `Method` by name/signature.

### 5. Linear route scan with regex — done
`server/src/klite/Router.kt`

Routes are bucketed by `RequestMethod`. Static paths (no regex metacharacters) are matched via a `Map<String, Route>` lookup; only routes with path params / custom regex are iterated. HEAD falls back to GET.

## Medium impact

### 6. Join-alias regex on every query — done
`jdbc/src/ResultSets.kt:115`

`withJoinPrefixes` always runs `joinRegex.findAll`. Guard with `select.contains("join", ignoreCase = true)` so plain selects skip the regex.

### 7. `JsonParser` allocates a `StringBuilder` per string/number token
`json/src/JsonParser.kt:35`, `:129`

Reuse one buffer instance for the parse.

### 8. Unicode escape path allocates list + string — done
`json/src/JsonParser.kt:48`

```kotlin
'u' -> (1..4).map { next() }.joinToString("").toInt(16).toChar()
```

Fold 4 hex digits with bit shifts.

### 9. `JsonRenderer.flush` copies the buffer via `toString()` and always `flush()`es the writer
`json/src/JsonRenderer.kt:95-98`

Mid-payload flushes hit the network. Prefer `out.write(buf, 0, len)` / a `CharArray` sink and flush only on `close`/`render` end.

### 10. `JdbcConverter.to` double map lookup
`jdbc/src/JdbcConverter.kt:61-62`

```kotlin
converters.contains(cls) -> (converters[cls] as ToJdbcConverter<Any>).invoke(v, conn)
```

Use `converters[cls]?.let { ... }` or `getOrDefault` once.

### 11. `MultipartParser.readLine` is byte-at-a-time `read()` — done
`server/src/klite/MultipartParser.kt:55-65`

Lines are now read from a buffer, but whole parts are still kept in memory. Fine for small forms; painful for large uploads.

### 12. `PathParams.get` uses `runCatching` for missing names — done
`server/src/klite/Router.kt:137`

```kotlin
override fun get(key: String) = runCatching { groups?.get(key) }.getOrNull()?.value
```

Named-group lookup throws when the key is absent — exception as control flow on optional path params. Prefer `groups?.let { if (key in namedGroups) it[key] else null }` or a name→index map built once.

### 13. `Accept` does substring `contains` and is rebuilt
`server/src/klite/Body.kt:13-16`, `HttpExchange.kt:105`

`contentTypes.contains(contentType)` is O(n) and not token-aware; `accept` allocates a new `Accept` whenever `findRenderer`/`responseType` runs.

### 14. SQL string building allocates intermediate collections — done
`jdbc/src/JdbcExtensions.kt:205`, `:132-133`

```kotlin
internal fun DB.setExpr(values: ValueMap) = values.entries.map { (k, v) -> k to v }.join(", ")
val keyValuesToSet = values.map { it.filter { it.value !is GeneratedKey<*> } }
```

`map { k to v }` rebuilds pairs that `joinToString` could write directly; `insertBatch` copies every row map even when nothing is a `GeneratedKey`.

### 15. `getOptional` exception-driven — done — done
`jdbc/src/ResultSets.kt:31`, used from `Values.kt:33` for optional columns

Missing optional columns throw/catch via `runCatching` on every entity. Cheaper: probe column presence once (metadata) or use `findColumn` result cached per mapper.

## Lower / correctness-adjacent

| Spot | Issue |
|------|--------|
| `core/src/Decimal.kt:24` | `constructor(v: String): this(v.toDouble())` goes through `Double` (precision loss for large values) |
| `csv/src/CSVGenerator.kt:9,21` | regex `needsQuotes` per cell |
| `csv/src/CSVParser.kt:8,31` | heavy regex per line |
| `core/src/KeyConverter.kt:14-15` | `SnakeCase` regex/split per key — hot when used as JSON `KeyConverter` |
| `json/src/JsonRenderer.kt:29` | `Converter.supports(o::class)` on every non-trivial value (map lookup + `forceInit` on miss) |
| `jdbc/src/SqlExpr.kt:84-89` | `seqExpr` rebuilds `join`/`flatValues` on every `expr()`/`values()` call |

## Already in good shape
- `colNameCache`, `publicPropsCache`, `annotationMetaCache`, `classCreators`, `inlineClassesAsString`
- `JsonParser` char buffer with `unRead`
- Cached SQL constants (`selectFrom`, etc.)
- `Decimal` fixed-point arithmetic (intentionally faster than `BigDecimal`)

## Found by measuring — done

| Spot | Issue | Gain |
|------|-------|------|
| `HttpExchange.startResponse` | forced the lazy `session` on every response, even if unused | cookie decrypt (~1.4µs) or a DB query with `DBSessionStore` per request |
| `JsonParser.substituteArguments` | `KType.toString()` (~400ns) for every property, even for non-generic classes | typed parse ~2x faster |
| `JsonRenderer`/`JsonParser`/`JsonMapper.render` | 8K buffers allocated (zeroed) for every call | render `{"a":1}` to String 920 -> 90ns, parse 1200 -> 380ns |
| `annotations.Param` | `isSubclassOf(Iterable::class)` (~140ns) per `@QueryParam` per request | computed once |

## Measured, not worth optimizing
- JDBC row mapping: `ResultSet.create` ~1µs per 7-column row, no dominant part; `JdbcConverter.to`/`from` 30-65ns per value
- `PooledDataSource` sets `ApplicationName` on every acquire (a DB round trip on Postgres), but it is the liveness check and makes connections traceable on the DB side
