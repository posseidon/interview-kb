package io.github.posseidon.knowledgebase.it.interview.dto;

import java.util.List;
import java.util.UUID;

public record QuestionView(
    UUID id,
    String externalId,
    String content,
    boolean requiresImpl,
    String language,
    Integer frequency,
    List<String> topics,
    List<String> tags,
    List<AnswerView> answers
) {
}
