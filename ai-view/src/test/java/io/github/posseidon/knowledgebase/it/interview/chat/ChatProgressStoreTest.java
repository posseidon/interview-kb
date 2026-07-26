package io.github.posseidon.knowledgebase.it.interview.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChatProgressStoreTest {

  @Test
  void beginSetsDefaultStep() {
    ChatProgressStore store = new ChatProgressStore();

    store.begin("conversation-1");

    assertThat(store.currentStep("conversation-1")).contains("Thinking…");
  }

  @Test
  void stepOverwritesCurrentStepForActiveConversation() {
    ChatProgressStore store = new ChatProgressStore();
    store.begin("conversation-1");

    store.step("Searching questions about \"Kafka\"…");

    assertThat(store.currentStep("conversation-1"))
        .contains("Searching questions about \"Kafka\"…");
  }

  @Test
  void stepIsNoOpWhenNoConversationHasBegunOnThisThread() {
    ChatProgressStore store = new ChatProgressStore();

    store.step("orphaned step");

    assertThat(store.currentStep("conversation-1")).isEmpty();
  }

  @Test
  void endClearsStepForConversation() {
    ChatProgressStore store = new ChatProgressStore();
    store.begin("conversation-1");
    store.step("in progress");

    store.end("conversation-1");

    assertThat(store.currentStep("conversation-1")).isEmpty();
  }

  @Test
  void currentStepIsEmptyForUnknownConversation() {
    ChatProgressStore store = new ChatProgressStore();

    assertThat(store.currentStep("never-started")).isEmpty();
  }

  @Test
  void tracksMultipleConversationsIndependently() {
    ChatProgressStore store = new ChatProgressStore();
    store.begin("conversation-1");
    store.step("step for conversation 1");
    store.begin("conversation-2");
    store.step("step for conversation 2");

    assertThat(store.currentStep("conversation-1")).contains("step for conversation 1");
    assertThat(store.currentStep("conversation-2")).contains("step for conversation 2");
  }
}
