package klite.ai

import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.verbs.expect
import klite.Labeled
import klite.Metrics
import klite.ai.AIClient.TokenUsage
import org.junit.jupiter.api.Test

class TokenMetricsTest {
  @Test fun `accumulates usage per model`() {
    TokenMetrics.record(TokenUsage("m1", input = 10, output = 20, cached = 5, thought = 3))
    TokenMetrics.record(TokenUsage("m1", input = 1, output = 2))
    TokenMetrics.record(TokenUsage("m2", input = 7, output = 8))

    fun total(model: String, type: String, value: Long) = Labeled(mapOf("gen_ai_response_model" to model, "gen_ai_token_type" to type), value)
    @Suppress("UNCHECKED_CAST")
    val metrics = Metrics.data["gen_ai_client_token_usage_total"] as List<Labeled>
    expect(metrics).toContain(total("m1", "input", 11), total("m1", "output", 22), total("m1", "cached", 5), total("m1", "thought", 3),
      total("m2", "input", 7), total("m2", "output", 8))
  }
}
