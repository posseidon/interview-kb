package io.github.posseidon.knowledgebase.it.interview.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

/**
 * Records per-call LLM token usage into the shared {@code ingest.job.tokens} Micrometer counter
 * family (tagged {@code job}/{@code type}) — every ingest-app LLM call site (batch skill-level
 * classification, ingestion-time auto-merge rephrasing) needs this exact bookkeeping, so it lives
 * in one place instead of being reimplemented per caller.
 */
@Component
public class LlmTokenMetrics {

  private final MeterRegistry meterRegistry;

  public LlmTokenMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void record(String job, ChatResponse chatResponse) {
    if (chatResponse == null || chatResponse.getMetadata() == null) {
      return;
    }
    Usage usage = chatResponse.getMetadata().getUsage();
    if (usage == null) {
      return;
    }
    increment(job, "prompt", usage.getPromptTokens());
    increment(job, "completion", usage.getCompletionTokens());
    increment(job, "total", usage.getTotalTokens());
  }

  private void increment(String job, String type, Integer count) {
    if (count == null) {
      return;
    }
    meterRegistry.counter("ingest.job.tokens", "job", job, "type", type).increment(count);
  }
}
