package io.github.posseidon.knowledgebase.it.interview.chat;

public record ChatMessageView(String role, String content, String contentHtml) {

  public boolean isUser() {
    return "user".equals(role);
  }
}
