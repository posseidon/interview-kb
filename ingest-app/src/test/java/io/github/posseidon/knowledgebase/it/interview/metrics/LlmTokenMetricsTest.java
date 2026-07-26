package io.github.posseidon.knowledgebase.it.interview.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;

class LlmTokenMetricsTest {

  private static ChatResponse chatResponseWithUsage(int promptTokens, int completionTokens) {
    ChatResponseMetadata metadata = ChatResponseMetadata.builder()
        .usage(new DefaultUsage(promptTokens, completionTokens))
        .build();
    return new ChatResponse(List.of(), metadata);
  }

  @Test
  void recordsPromptCompletionAndTotalTokensTaggedByJob() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    LlmTokenMetrics metrics = new LlmTokenMetrics(registry);

    metrics.record("some_job", chatResponseWithUsage(100, 20));

    assertThat(registry.get("ingest.job.tokens").tag("job", "some_job").tag("type", "prompt")
        .counter().count()).isEqualTo(100);
    assertThat(registry.get("ingest.job.tokens").tag("job", "some_job").tag("type", "completion")
        .counter().count()).isEqualTo(20);
    assertThat(registry.get("ingest.job.tokens").tag("job", "some_job").tag("type", "total")
        .counter().count()).isEqualTo(120);
  }

  @Test
  void doesNothingWhenChatResponseIsNull() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    LlmTokenMetrics metrics = new LlmTokenMetrics(registry);

    metrics.record("some_job", null);

    assertThat(registry.getMeters()).isEmpty();
  }

  @Test
  void doesNothingWhenUsageIsAbsent() {
    // ChatResponseMetadata's own no-arg constructor always defaults usage to a non-null
    // EmptyUsage, so a genuinely null Usage (this guard clause) can only be exercised via a mock,
    // not through ChatResponse's real constructors.
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    LlmTokenMetrics metrics = new LlmTokenMetrics(registry);
    ChatResponse chatResponse = mock(ChatResponse.class);
    ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
    when(chatResponse.getMetadata()).thenReturn(metadata);
    when(metadata.getUsage()).thenReturn(null);

    metrics.record("some_job", chatResponse);

    assertThat(registry.getMeters()).isEmpty();
  }
}
