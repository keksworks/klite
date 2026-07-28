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
