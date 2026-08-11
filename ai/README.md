# klite-ai

Experimental AI client integrations and PDF data extraction.

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

## Usage

### AI Clients

Both `OpenAIClient` and `GeminiClient` implement the `AIClient` interface:

```kotlin
val client: AIClient = OpenAIClient(httpClient)
val answer = client.query("What is 2 + 2?")
```

Register the client you want to use:
```kotlin
register<AIClient>(OpenAIClient::class) // or GeminiClient::class
```

### PDFExtractor

Extracts text from PDFs and uses an AI client to parse structured data:

```kotlin
val extractor = PDFExtractor(OpenAIClient(httpClient))

// Extract raw text
val text = extractor.extractText(pdfInputStream)

// Extract structured data into a data class
data class Invoice(val number: String, val amount: String, val date: String)

val invoice = extractor.extractData<Invoice>(pdfInputStream)

// With pre-filled fields (excluded from AI prompt)
val invoice = extractor.extractData<Invoice>(pdfInputStream,
  mapOf(Invoice::id to myId, Invoice::partnerId to partnerId))

// With extra prompt instructions
val invoice = extractor.extractData<Invoice>(pdfInputStream, extraPrompt = "The currency is always EUR")
```

The `extractData` method:
1. Extracts text from the PDF using PDFBox
2. Sends the text to the AI client with a prompt describing the expected JSON structure, which is derived from the provided data class
3. Parses the AI response into the target data class
4. Retries up to 3 times on failure (except 429 Too Many Requests)

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
