package io.github.posseidon.knowledgebase.it.interview.dto.interview;

import io.github.posseidon.knowledgebase.it.interview.domain.interview.Decision;

import java.util.List;
import java.util.UUID;

public record InterviewView(
    UUID id,
    String projectCode,
    String dateDisplay,
    Decision decision,
    String decisionCssClass,
    String decisionLabel,
    String feedbackHtml,
    String upskillingPlanHtml,
    String feedbackSnippet,
    int questionCount,
    List<SkillGroup> questionsBySkill
) {

}
