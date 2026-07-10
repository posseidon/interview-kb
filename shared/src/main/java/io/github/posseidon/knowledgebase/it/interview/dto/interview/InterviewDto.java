package io.github.posseidon.knowledgebase.it.interview.dto.interview;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.posseidon.knowledgebase.it.interview.domain.interview.Decision;
import io.github.posseidon.knowledgebase.it.interview.dto.ingest.request.QuestionDto;

import java.time.LocalDate;
import java.util.List;

public record InterviewDto(
    @JsonProperty("project_code") String projectCode,
    LocalDate date,
    String feedback,
    @JsonProperty("upskilling_plan") String upskillingPlan,
    Decision decision,
    /** External-id references to questions already in the KB. */
    @JsonProperty("question_ids") List<String> questionIds,
    /** Inline question definitions — upserted into the KB and linked. */
    List<QuestionDto> questions
) {

}
