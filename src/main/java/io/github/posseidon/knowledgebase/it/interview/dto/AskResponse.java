package io.github.posseidon.knowledgebase.it.interview.dto;

import java.util.List;

public record AskResponse(
    String answer,
    List<QuestionView> sources
) {
}
