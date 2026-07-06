package io.github.posseidon.knowledgebase.it.interview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record QuestionDto(
    @JsonProperty("external_id") String externalId,
    String content,
    @JsonProperty("requires_impl") boolean requiresImpl,
    String language,
    List<String> topics,
    List<String> tags,
    List<AnswerDto> answers
) {
}
