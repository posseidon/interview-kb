package io.github.posseidon.knowledgebase.it.interview.dto.question;

import java.util.UUID;

public record AnswerView(
        UUID id,
        String source,
        String content
) {
}
