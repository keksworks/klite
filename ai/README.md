# klite-ai

Experimental AI client integrations and structured data extraction.

## Configuration

### OpenAI

| Env var / System property | Required | Description |
|---|---|---|
| `OPENAI_API_KEY` | yes | OpenAI API key |
| `OPENAI_MODEL` | yes | Default model, e.g. `gpt-4o` |

### Gemini

| Env var / System property | Required | Description |
|---|---|---|
| `GEMINI_API_KEY` | yes | Google AI API key |
| `GEMINI_MODEL` | yes | Default model, e.g. `gemini-2.5-pro` |

### Cloudflare Workers AI (Clairic)

| Env var / System property | Required | Description |
|---|---|---|
| `CLOUDFLARE_ACCOUNT_ID` | yes | Cloudflare account ID |
| `CLOUDFLARE_API_TOKEN` | yes | API token with Workers AI access |
| `CLOUDFLARE_MODEL` | no | Model ID, `@cf/cloudflare/clairic` by default (can be `clairic-flash`) |
| `CLOUDFLARE_URL` | no | Overrides the account-scoped `.../ai/run` URL |

## Usage

### AI Clients

`OpenAIClient` and `GeminiClient` implement the `AIClient` interface:

```kotlin
val client: AIClient = OpenAIClient(httpClient)
val answer = client.query("What is 2 + 2?")
```

`CloudFlareClient` uses the Clairic extraction model instead of generating text, so it implements `TypedExtractionClient` and is meant to be passed to `DataExtractor`:

```kotlin
val extractor = DataExtractor(CloudFlareClient(httpClient))
```

Clairic takes a text state plus up to four images or PDFs and returns typed fields, so `DataExtractor` converts the Kotlin data class schema into Clairic fields automatically. `DataExtractor` skips `id`, `createdAt` and `updatedAt` when they have default values, for every AI client. Nested objects are supported only one level deep (and their properties must be text, number, boolean or enum); lists nested inside object rows are requested as comma-separated text and split back into lists.

Register the client you want to use:
```kotlin
register<AIClient>(OpenAIClient::class) // or GeminiClient::class
```

### DataExtractor

Extracts structured data from text, images or documents (e.g. PDFs) using an AI client:

```kotlin
val extractor = DataExtractor(OpenAIClient(httpClient))

data class Invoice(val number: String, val amount: String, val date: String)

// Files can be local or remote, the whole content is sent to the AI client
val invoice = extractor.extract<Invoice>("Extract the invoice", File("invoice.pdf").toURI())

// With pre-filled fields (excluded from AI prompt)
val invoice = extractor.extract<Invoice>("", pdfFile.toURI(),
  provided = mapOf(Invoice::id to myId, Invoice::partnerId to partnerId))

// With extra prompt instructions
val invoice = extractor.extract<Invoice>("The currency is always EUR", pdfFile.toURI())
```

The `extract` method:
1. Sends the text and the whole content of any given files to the AI client with a prompt describing the expected JSON structure, which is derived from the provided data class
2. Parses the AI response into the target data class
3. Retries up to 3 times on failure (except 429 Too Many Requests)

`PDFExtractor` is deprecated: it no longer extracts text locally with PDFBox (which is not a dependency anymore), but simply forwards the whole file to `DataExtractor`, so use `DataExtractor` directly.

## MCP Server

Build an [MCP](https://modelcontextprotocol.io)-compatible server by extending `McpRoutes`:

```kotlin
class MyMcpRoutes(info: ServerInfo = ServerInfo("My Server", "1.0")) : McpRoutes(info) {
  // Register tool functions as (method reference, description) pairs
  override val tools: List<Pair<KFunction<*>, String>> by lazy { listOf(
    this::search to "Search the database",
    this::getById to "Get item by ID",
  ) }

  // Authenticate the request; return a context object passed to every tool, or null to reject
  override fun authenticate(exchange: HttpExchange): String? =
    exchange.header("Authorization")?.removePrefix("Bearer ")

  // First parameter is always the auth context from authenticate()
  fun search(user: String, query: String, limit: Int = 10): List<String> = ...
  fun getById(user: String, id: Long): Item = ...
}
```

Tool parameter types are derived from the function signature:
- Non-nullable parameters become `required` in the JSON schema
- Nullable parameters (with defaults) are optional
- Enums are exposed with a `enum` constraint
- Supported types: `String`, `Int`, `Long`, `Double`, `Boolean`

Then register the routes on your server:

```kotlin
context("/mcp") {
  use<JsonBody>()
  annotated<MyMcpRoutes>()
}
```

The server exposes a single `POST /` endpoint that handles all MCP JSON-RPC requests (`tools/list`, `tools/call`, etc).

See [a working example in StoryTracker project](https://github.com/keksworks/storytracker/blob/main/src/mcp/McpRoutes.kt).
