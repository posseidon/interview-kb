package io.github.posseidon.knowledgebase.it.interview.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

@Service
public class ChatAgentService {

  private final ChatClient chatClient;
  private final ChatProgressStore progressStore;

  public ChatAgentService(ChatClient chatClient, ChatProgressStore progressStore) {
    this.chatClient = chatClient;
    this.progressStore = progressStore;
  }

  public void respondAsync(String conversationId, String userMessage) {
    Thread.ofVirtual()
        .name("chat-agent-" + conversationId)
        .start(() -> respond(conversationId, userMessage));
  }

  private void respond(String conversationId, String userMessage) {
    progressStore.begin(conversationId);
    try {
      chatClient.prompt()
          .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
          .user(userMessage)
          .call()
          .content();
    } finally {
      progressStore.end(conversationId);
    }
  }
}
