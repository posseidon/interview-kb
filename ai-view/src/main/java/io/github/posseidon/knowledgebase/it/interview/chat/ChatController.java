package io.github.posseidon.knowledgebase.it.interview.chat;

import io.github.posseidon.knowledgebase.it.interview.util.Markdown;
import io.github.posseidon.knowledgebase.it.interview.web.GenerationStatusView;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/chat")
public class ChatController {

  private final ChatAgentService chatAgentService;
  private final ChatMemory chatMemory;
  private final ChatProgressStore progressStore;

  public ChatController(ChatAgentService chatAgentService, ChatMemory chatMemory,
      ChatProgressStore progressStore) {
    this.chatAgentService = chatAgentService;
    this.chatMemory = chatMemory;
    this.progressStore = progressStore;
  }

  private static ChatMessageView toView(Message message) {
    boolean isUser = message.getMessageType() == MessageType.USER;
    String text = message.getText();
    return new ChatMessageView(isUser ? "user" : "assistant", text,
        isUser ? null : Markdown.toHtml(text));
  }

  @GetMapping
  public String chat(HttpSession session, Model model) {
    model.addAttribute("messages", messagesFor(session.getId()));
    return "chat/chat";
  }

  @PostMapping("/messages")
  public String send(@RequestParam String message, HttpSession session) {
    if (message != null && !message.isBlank()) {
      chatAgentService.respondAsync(session.getId(), message);
    }
    return "redirect:/chat";
  }

  @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public GenerationStatusView status(HttpSession session) {
    return progressStore.currentStep(session.getId())
        .map(step -> new GenerationStatusView(true, step))
        .orElseGet(() -> new GenerationStatusView(false, null));
  }

  private List<ChatMessageView> messagesFor(String conversationId) {
    return chatMemory.get(conversationId).stream()
        .filter(m -> m.getMessageType() == MessageType.USER
            || m.getMessageType() == MessageType.ASSISTANT)
        .filter(m -> m.getText() != null && !m.getText().isBlank())
        .map(ChatController::toView)
        .toList();
  }
}
