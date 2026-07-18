package io.github.posseidon.knowledgebase.it.interview.dto.question;

import io.github.posseidon.knowledgebase.it.interview.domain.skill.SkillLevel;
import java.util.List;
import java.util.UUID;

public record QuestionView(
    UUID id,
    String externalId,
    String content,
    boolean requiresImpl,
    String language,
    Integer frequency,
    SkillLevel level,
    List<SkillRef> skills,
    List<AnswerView> answers
) {

}
