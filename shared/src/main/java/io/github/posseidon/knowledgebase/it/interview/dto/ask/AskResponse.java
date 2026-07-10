package io.github.posseidon.knowledgebase.it.interview.dto.ask;

import io.github.posseidon.knowledgebase.it.interview.dto.question.QuestionView;

import java.util.List;

public record AskResponse(
        String answer,
        List<QuestionView> sources
) {
}
