package io.github.posseidon.knowledgebase.it.interview.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.posseidon.knowledgebase.it.interview.web.GenerationStatusView;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

class ChatControllerTest {

  private ChatAgentService chatAgentService;
  private ChatMemory chatMemory;
  private ChatProgressStore progressStore;
  private ChatController controller;
  private MockHttpSession session;

  @BeforeEach
  void setUp() {
    chatAgentService = mock(ChatAgentService.class);
    chatMemory = mock(ChatMemory.class);
    progressStore = new ChatProgressStore();
    controller = new ChatController(chatAgentService, chatMemory, progressStore);
    session = new MockHttpSession();
  }

  @Test
  void chatRendersHistoryFilteredToUserAndAssistantTurns() {
    when(chatMemory.get(session.getId())).thenReturn(List.of(
        new SystemMessage("you are an assistant"),
        new UserMessage("What is Kafka?"),
        new AssistantMessage(""),
        new AssistantMessage("Kafka is a **streaming** platform.")
    ));

    Model model = new ExtendedModelMap();
    String view = controller.chat(session, model);

    assertThat(view).isEqualTo("chat/chat");
    @SuppressWarnings("unchecked")
    List<ChatMessageView> messages = (List<ChatMessageView>) model.getAttribute("messages");
    assertThat(messages).hasSize(2);
    assertThat(messages.get(0).isUser()).isTrue();
    assertThat(messages.get(0).content()).isEqualTo("What is Kafka?");
    assertThat(messages.get(1).isUser()).isFalse();
    assertThat(messages.get(1).contentHtml()).contains("<strong>streaming</strong>");
  }

  @Test
  void chatRendersEmptyListForNewConversation() {
    when(chatMemory.get(session.getId())).thenReturn(List.of());

    Model model = new ExtendedModelMap();
    controller.chat(session, model);

    assertThat((List<?>) model.getAttribute("messages")).isEmpty();
  }

  @Test
  void sendDelegatesToChatAgentServiceAndRedirects() {
    String result = controller.send("What is a partition?", session);

    assertThat(result).isEqualTo("redirect:/chat");
    verify(chatAgentService).respondAsync(eq(session.getId()), eq("What is a partition?"));
  }

  @Test
  void sendIgnoresBlankMessages() {
    String result = controller.send("   ", session);

    assertThat(result).isEqualTo("redirect:/chat");
    verify(chatAgentService, never()).respondAsync(any(), any());
  }

  @Test
  void statusReportsNotPendingForFreshConversation() {
    GenerationStatusView status = controller.status(session);

    assertThat(status.pending()).isFalse();
    assertThat(status.step()).isNull();
  }

  @Test
  void statusReportsCurrentStepWhilePending() {
    progressStore.begin(session.getId());
    progressStore.step("Searching questions about \"Kafka\"…");

    GenerationStatusView status = controller.status(session);

    assertThat(status.pending()).isTrue();
    assertThat(status.step()).isEqualTo("Searching questions about \"Kafka\"…");
  }
}
