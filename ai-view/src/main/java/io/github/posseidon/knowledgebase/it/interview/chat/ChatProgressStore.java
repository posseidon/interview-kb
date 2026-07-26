package io.github.posseidon.knowledgebase.it.interview.chat;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Tracks a human-readable "what's happening right now" step per conversation, so the chat page can
 * poll and show progress while the agent's tool-calling loop runs on a background thread. The
 * current conversation id is carried on a {@link ThreadLocal} set by {@link #begin(String)} — tool
 * methods call {@link #step(String)} without needing to know which conversation they're running
 * for, since they execute on that same thread.
 */
@Component
public class ChatProgressStore {

  private static final String DEFAULT_STEP = "Thinking…";

  private static final ThreadLocal<String> CURRENT_CONVERSATION = new ThreadLocal<>();

  private final Map<String, String> steps = new ConcurrentHashMap<>();

  public void begin(String conversationId) {
    CURRENT_CONVERSATION.set(conversationId);
    steps.put(conversationId, DEFAULT_STEP);
  }

  public void step(String description) {
    String conversationId = CURRENT_CONVERSATION.get();
    if (conversationId != null) {
      steps.put(conversationId, description);
    }
  }

  public void end(String conversationId) {
    steps.remove(conversationId);
    CURRENT_CONVERSATION.remove();
  }

  public Optional<String> currentStep(String conversationId) {
    return Optional.ofNullable(steps.get(conversationId));
  }
}
