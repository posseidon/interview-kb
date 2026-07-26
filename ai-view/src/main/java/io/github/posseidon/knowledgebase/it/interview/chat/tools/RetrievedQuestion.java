package io.github.posseidon.knowledgebase.it.interview.chat.tools;

import java.util.List;
import java.util.UUID;

public record RetrievedQuestion(
    UUID id,
    String content,
    List<String> skills,
    List<String> answers) {

}
