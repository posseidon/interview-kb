package io.github.posseidon.knowledgebase.it.interview.dto.ingest.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.posseidon.knowledgebase.it.interview.domain.skill.SkillLevel;
import java.util.List;

public record QuestionDto(
    @JsonProperty("external_id") String externalId,
    String content,
    @JsonProperty("requires_impl") boolean requiresImpl,
    String language,
    List<String> skills,
    List<AnswerDto> answers,
    SkillLevel level
) {
    public QuestionDto {
        if (level == null) level = SkillLevel.NOVICE;
    }
}
