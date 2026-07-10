package io.github.posseidon.knowledgebase.it.interview.dto.question;

import java.util.List;
import java.util.UUID;

public record QuestionView(
        UUID id,
        String externalId,
        String content,
        boolean requiresImpl,
        String language,
        Integer frequency,
        List<SkillRef> skills,
        List<AnswerView> answers
) {
}
