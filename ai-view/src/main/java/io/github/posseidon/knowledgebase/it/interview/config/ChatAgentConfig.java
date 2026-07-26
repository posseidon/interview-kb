package io.github.posseidon.knowledgebase.it.interview.config;

import io.github.posseidon.knowledgebase.it.interview.chat.tools.QuestionBankTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatAgentConfig {

  private static final int MAX_MEMORY_MESSAGES = 20;

  private static final String SYSTEM_PROMPT = """
      You are the AI assistant for an IT interview knowledge base. You help the user:
      - browse existing interview questions by topic or skill
      - answer factual questions using only the answers stored in the knowledge base
      - match a pasted job description against relevant existing questions
      - generate a quiz on a named skill
      - generate a series of quizzes from a project or position description
      
      Always call the appropriate tool before answering — never invent facts, questions, or
      answers that the tools did not return. If a tool returns no results, say so plainly instead
      of making something up. When building a quiz, phrase each retrieved question clearly and
      give the retrieved answer as the expected answer, grouped by skill when there are several.
      """;

  @Bean
  public ChatMemory chatMemory() {
    return MessageWindowChatMemory.builder()
        .chatMemoryRepository(new InMemoryChatMemoryRepository())
        .maxMessages(MAX_MEMORY_MESSAGES)
        .build();
  }

  @Bean
  public ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory,
      QuestionBankTools questionBankTools) {
    return builder
        .defaultSystem(SYSTEM_PROMPT)
        .defaultTools(questionBankTools)
        .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
        .build();
  }

  /**
   * A separate, plain {@link ChatClient} for one-shot, memory-less generation tasks (per-question
   * quiz generation). Deliberately carries neither {@code defaultTools} nor a
   * {@link MessageChatMemoryAdvisor} — those requests aren't part of a conversation and don't need
   * retrieval, since the caller already supplies the exact grounding content in the prompt.
   * {@code ChatClient.Builder} is prototype-scoped, so this gets its own independent instance from
   * the {@code chatClient} bean above.
   */
  @Bean
  public ChatClient quizChatClient(ChatClient.Builder builder) {
    return builder.build();
  }
}
