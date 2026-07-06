package io.github.posseidon.knowledgebase.it.interview.dto;

import java.util.List;

public record IngestRequest(
    List<TopicDto> topics,
    List<QuestionDto> questions
) {
}
