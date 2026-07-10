package io.github.posseidon.knowledgebase.it.interview.dto.interview;

import io.github.posseidon.knowledgebase.it.interview.dto.question.QuestionView;

import java.util.List;

public record SkillGroup(String skillName, List<QuestionView> questions) {

}
